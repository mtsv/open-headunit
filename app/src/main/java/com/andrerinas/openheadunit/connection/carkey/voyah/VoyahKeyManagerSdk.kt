package com.andrerinas.openheadunit.connection.carkey.voyah

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IInterface
import com.andrerinas.openheadunit.utils.AppLog
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Finds the Voyah key-manager client class on the unit and describes it for [KeyManagerApiShape].
 *
 * The class is not in OHU's APK, so it is loaded from where the OEM keeps it: the framework jars
 * (VoyahTune found its CAN SDK as `/system/framework/QGVehicle.jar`), then the system APKs whose
 * apps log using it. Only system packages are trusted as a source; code from a user-installed APK
 * claiming to be the OEM's is never loaded.
 */
object VoyahKeyManagerSdk {

    const val SERVICE_PACKAGE = "com.qinggan.keymanager.service"

    /** Apps seen registering key listeners, in the order worth trying. */
    private val SOURCE_PACKAGES = listOf(
        SERVICE_PACKAGE,
        "com.qinggan.app.vehicle",
        "com.qinggan.bluetoothphone",
        "com.qinggan.sttservice",
        "com.qinggan.app.launcher",
    )

    data class Loaded(val managerClass: Class<*>, val source: String)

    /** A [KeyManagerApiShape.MethodRef] paired with the method it describes. */
    data class Described(
        val constructors: List<List<KeyManagerApiShape.TypeRef>>,
        val methods: Map<KeyManagerApiShape.MethodRef, Method>,
    )

    fun isServiceInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(SERVICE_PACKAGE, 0)
    }.isSuccess

    fun load(context: Context): Loaded? {
        val parent = context.classLoader
        val sources = mutableListOf<Pair<String, () -> ClassLoader>>("app" to { parent })
        File("/system/framework").listFiles { f ->
            f.isFile && f.name.endsWith(".jar") &&
                (f.name.startsWith("QG", ignoreCase = true) || f.name.contains("qinggan", ignoreCase = true))
        }?.sortedBy { it.name }?.forEach { jar ->
            sources += jar.path to { PathClassLoader(jar.path, parent) }
        }
        for (pkg in SOURCE_PACKAGES) {
            val info = try {
                context.packageManager.getApplicationInfo(pkg, PackageManager.GET_SHARED_LIBRARY_FILES)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                AppLog.w("VoyahKeys: $pkg is not a system app; not loading code from it")
                continue
            }
            val paths = listOfNotNull(*(info.sharedLibraryFiles ?: emptyArray()), info.sourceDir) +
                (info.splitSourceDirs?.toList() ?: emptyList())
            sources += pkg to { PathClassLoader(paths.joinToString(File.pathSeparator), parent) }
        }
        for ((name, loader) in sources) {
            try {
                val cls = Class.forName(KeyManagerApiShape.MANAGER_CLASS, false, loader())
                return Loaded(cls, name)
            } catch (e: ClassNotFoundException) {
                // next source
            } catch (e: LinkageError) {
                AppLog.w("VoyahKeys: ${KeyManagerApiShape.MANAGER_CLASS} in $name failed to link: $e")
            }
        }
        AppLog.w("VoyahKeys: ${KeyManagerApiShape.MANAGER_CLASS} not found in ${sources.map { it.first }}")
        return null
    }

    fun describe(cls: Class<*>): Described {
        val constructors = cls.constructors.map { c -> c.parameterTypes.map(::typeRef) }
        val methods = cls.methods
            .filter { it.declaringClass != Any::class.java }
            .associateBy { m ->
                KeyManagerApiShape.MethodRef(
                    name = m.name,
                    params = m.parameterTypes.map(::typeRef),
                    returns = m.returnType.name,
                    isStatic = Modifier.isStatic(m.modifiers),
                )
            }
        return Described(constructors, methods)
    }

    /**
     * Logs the whole public surface: the manager, its nested types, and every interface its
     * register methods take. On a car where [KeyManagerApiShape] refuses the shape, this is the
     * line that says what it should have accepted.
     */
    fun dump(loaded: Loaded) {
        val cls = loaded.managerClass
        val lines = mutableListOf("VoyahKeys: ${cls.name} from ${loaded.source}")
        cls.constructors.forEach { lines += "  ctor(${it.parameterTypes.joinToString { t -> t.name }})" }
        cls.methods.filter { it.declaringClass != Any::class.java }.sortedBy { it.name }.forEach {
            lines += "  ${signature(it)}"
        }
        val related = (cls.classes.toList() + cls.methods.flatMap { it.parameterTypes.toList() })
            // Classes too: a listener that is an abstract AIDL Stub is the shape most worth seeing.
            .filter { it.name.startsWith("com.qinggan") }
            .distinct()
        for (type in related) {
            lines += "  ${if (type.isInterface) "interface" else "class"} ${type.name}"
            type.methods.filter { it.declaringClass != Any::class.java }.forEach { lines += "    ${signature(it)}" }
        }
        lines.forEach { AppLog.i(it) }
    }

    private fun signature(m: Method): String =
        (if (Modifier.isStatic(m.modifiers)) "static " else "") +
            "${m.returnType.name} ${m.name}(${m.parameterTypes.joinToString { it.name }})"

    private fun typeRef(c: Class<*>) =
        KeyManagerApiShape.TypeRef(c.name, c.isInterface, IInterface::class.java.isAssignableFrom(c))
}
