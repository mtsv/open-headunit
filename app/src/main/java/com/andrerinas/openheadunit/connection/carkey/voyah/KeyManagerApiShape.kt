package com.andrerinas.openheadunit.connection.carkey.voyah

/**
 * Works out how to use the Voyah head unit's key-manager client SDK from its reflected shape.
 *
 * The OEM key service (`com.qinggan.keymanager.service`) is reached through a client class,
 * `com.qinggan.system.KeyManager`, that ships on the unit and not in any SDK OHU can compile
 * against. Its own apps log calling `registerInterceptListener(…, priority)` (the login screen, the
 * IME, power manager, vehicle and audio policy) and `registerKeyEventListener(…)` (the Bluetooth
 * phone, the speech service, the vehicle key tool), as captured in VoyahTune's `Utils/log/aaaa`.
 * So OHU uses the unit's own classes, loaded at run time, and this decides from what reflection
 * shows whether they have a shape OHU can drive:
 *
 * - an instance from a static `getInstance` or a constructor taking a `Context` and optionally an
 *   init listener (an interface named `…OnInitListener`, told when the binding is up);
 * - registration methods whose parameters are one plain **interface** (implemented with a
 *   `java.lang.reflect.Proxy`), optionally a `Context`, and optionally one `int`, taken as the
 *   priority. An abstract AIDL `Stub` class cannot be proxied, and neither, usefully, can an AIDL
 *   interface itself (one extending `android.os.IInterface`): the SDK would call `asBinder()` on
 *   it to send it to the service, and a proxy has no binder to give. Both are refused.
 *
 * The first car capture (2026-10-31, alpha-10) refused a real SDK because the guess allowed only a
 * bare `(Context)`: that unit has `getInstance(Context, OnInitListener)` and
 * `(Context, OnInitListener)`, `registerInterceptListener` in `(listener)`, `(listener, int)`,
 * `(listener, int[])`, `(listener, int[], int)` and `(String, listener[, int[]])`, and
 * `registerKeyEventListener` with no priority at all. Overloads taking a `String` or an `int[]`
 * filter are not used: what goes in them is not known. Of those left, the one that takes a priority
 * is preferred; two that fit equally well are still refused rather than picked between.
 *
 * Anything else is refused with a reason rather than guessed at: calling an OEM method with a
 * misread argument list could register OHU wrongly in the service that owns every key in the car.
 * The receiver logs the whole reflected API either way, so a refusal on a car is one log away
 * from a shape this can learn.
 *
 * Pure: it sees types only as [TypeRef]s, so the test can describe an SDK that does not exist on
 * the JVM.
 */
object KeyManagerApiShape {

    const val MANAGER_CLASS = "com.qinggan.system.KeyManager"

    /**
     * Above power manager (100) so OHU sees Voice before any stock handler that registered at 100
     * or below, and below the login screen and IME (`Int.MAX_VALUE`), which only take keys while
     * they are on screen and must keep them.
     */
    const val INTERCEPT_PRIORITY = 200

    /** Same as the stock phone and speech listeners, for an SDK whose observer takes one. */
    const val OBSERVE_PRIORITY = 50

    val INTERCEPT_NAMES = listOf("registerInterceptListener")
    val OBSERVE_NAMES = listOf("registerKeyEventListener", "registerKeyManagerListener")

    /** [isBinder]: the type extends `android.os.IInterface`, i.e. it is an AIDL interface. */
    data class TypeRef(val name: String, val isInterface: Boolean = false, val isBinder: Boolean = false)

    data class MethodRef(
        val name: String,
        val params: List<TypeRef>,
        val returns: String = "void",
        val isStatic: Boolean = false,
    )

    sealed interface Arg {
        object Context : Arg
        object Listener : Arg
        /** The init listener of [Instance]; a proxy whose `onResult(boolean)` says the SDK is bound. */
        object InitListener : Arg
        data class Priority(val value: Int) : Arg
    }

    sealed interface Instance {
        val args: List<Arg>
        data class StaticFactory(val method: MethodRef, override val args: List<Arg>) : Instance
        data class Constructor(val params: List<TypeRef>, override val args: List<Arg>) : Instance
    }

    data class Registration(
        val method: MethodRef,
        val listenerType: TypeRef,
        val args: List<Arg>,
        /** The matching `unregister…` taking the listener alone, if there is one. */
        val unregister: MethodRef?,
    )

    sealed interface Result {
        data class Usable(
            val instance: Instance,
            val intercept: Registration?,
            val observe: Registration?,
        ) : Result
        data class Refused(val why: String) : Result
    }

    /**
     * What a key listener callback, by its name on the SDK's listener interface, says happened.
     *
     * As captured on the car (2026-10-31, build 12), a short press is `onKeyDown` then
     * `onKeyShortClick` (~100–300 ms later); a long one is `onKeyDown`, then at ~1 s
     * `onKeyHoldPressStart` and `onKeyLongPressStart` together, then `onKeyLongPressStop` at release.
     * [longPhase] marks the callbacks that only a long press produces.
     */
    enum class Callback(val longPhase: Boolean) {
        /** The button went down (`onKeyDown`); short or long is not known yet. */
        DOWN(false),
        /** The release of a short press (`onKeyShortClick`). */
        SHORT_END(false),
        /** `onKeyHoldPressStart`, which arrives alongside [LONG]: not acted on. */
        HOLD(true),
        /** The service itself measured a long press (`onKeyLongPressStart`). */
        LONG(true),
        /** The release of a long press (`onKeyLongPressStop`, `onKeyHoldPressStop`). */
        LONG_END(true),
        /** Anything else: logged, not acted on, never consumed. */
        OTHER(false),
    }

    fun callbackFor(methodName: String): Callback = when (methodName) {
        "onKeyDown" -> Callback.DOWN
        "onKeyShortClick" -> Callback.SHORT_END
        "onKeyHoldPressStart" -> Callback.HOLD
        "onKeyLongPressStart" -> Callback.LONG
        "onKeyLongPressStop", "onKeyHoldPressStop" -> Callback.LONG_END
        else -> Callback.OTHER
    }

    fun resolve(managerClass: String, constructors: List<List<TypeRef>>, methods: List<MethodRef>): Result {
        val instance = instanceOf(managerClass, constructors, methods)
            ?: return Result.Refused("no getInstance or constructor taking (Context[, OnInitListener])")
        val intercept = registrationFor(methods, INTERCEPT_NAMES, INTERCEPT_PRIORITY)
        val observe = registrationFor(methods, OBSERVE_NAMES, OBSERVE_PRIORITY)
        if (intercept == null && observe == null) {
            return Result.Refused(
                "no register method takes one interface listener (+ optional Context, int)",
            )
        }
        return Result.Usable(instance, intercept, observe)
    }

    /** Prefers the forms with an init listener, which say when registering will work. */
    private fun instanceOf(
        managerClass: String,
        constructors: List<List<TypeRef>>,
        methods: List<MethodRef>,
    ): Instance? {
        val factories = methods.filter { it.isStatic && it.name == "getInstance" && it.returns == managerClass }
            .mapNotNull { m -> instanceArgs(m.params)?.let { Instance.StaticFactory(m, it) } }
        val ctors = constructors.mapNotNull { p -> instanceArgs(p)?.let { Instance.Constructor(p, it) } }
        return (factories + ctors).maxByOrNull { if (Arg.InitListener in it.args) 1 else 0 }
    }

    private fun instanceArgs(params: List<TypeRef>): List<Arg>? = when {
        params.map(TypeRef::name) == listOf(CONTEXT) -> listOf(Arg.Context)
        params.size == 2 && params[0].name == CONTEXT && isInitListener(params[1]) ->
            listOf(Arg.Context, Arg.InitListener)
        else -> null
    }

    private fun isInitListener(t: TypeRef) =
        t.isInterface && !t.isBinder && t.name.substringAfterLast('$').substringAfterLast('.') == "OnInitListener"

    private fun registrationFor(methods: List<MethodRef>, names: List<String>, priority: Int): Registration? {
        for (name in names) {
            val candidates = methods.filter { !it.isStatic && it.name == name }
                .mapNotNull { m -> argsFor(m, priority)?.let { m to it } }
            val best = candidates.filter { (_, args) -> args.any { it is Arg.Priority } }
                .ifEmpty { candidates }
            // Two overloads that fit equally well means the shape is ambiguous: refuse rather than pick.
            val (method, args) = best.singleOrNull() ?: continue
            val listener = method.params[args.indexOf(Arg.Listener)]
            val unregister = methods.firstOrNull {
                !it.isStatic && it.name == "un$name" && it.params == listOf(listener)
            }
            return Registration(method, listener, args, unregister)
        }
        return null
    }

    private fun argsFor(method: MethodRef, priority: Int): List<Arg>? {
        val args = method.params.map { p ->
            when {
                p.name == CONTEXT -> Arg.Context
                p.name == "int" -> Arg.Priority(priority)
                p.isInterface && !p.isBinder && !isInitListener(p) -> Arg.Listener
                else -> return null
            }
        }
        if (args.count { it == Arg.Listener } != 1) return null
        if (args.count { it is Arg.Priority } > 1 || args.count { it == Arg.Context } > 1) return null
        return args
    }

    private const val CONTEXT = "android.content.Context"
}
