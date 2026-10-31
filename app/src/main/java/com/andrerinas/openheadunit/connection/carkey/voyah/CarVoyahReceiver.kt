package com.andrerinas.openheadunit.connection.carkey.voyah

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.SteeringActionRunner
import com.andrerinas.openheadunit.aap.navigation.EncarsManeuverPolicy
import com.andrerinas.openheadunit.connection.carkey.CarKeyReceiver
import com.andrerinas.openheadunit.input.SteeringButton
import com.andrerinas.openheadunit.input.SteeringLongPressPolicy
import com.andrerinas.openheadunit.input.VoiceKeyConsumePolicy
import com.andrerinas.openheadunit.utils.AppLog
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.EnumMap

/**
 * The Voyah steering wheel's Voice and Phone buttons, from the head unit's own key service.
 *
 * Those presses never become Android key events or a broadcast: they go from the car to
 * `com.qinggan.keymanager.service`, which hands them to the OEM apps that registered with it.
 * Voyah Tweaks and VoyahTune both reach them by injecting a hook into that process, and two hooks
 * on the same method fight, so OHU registers through the OEM's client SDK like the stock apps do
 * ([VoyahKeyManagerSdk], [KeyManagerApiShape]):
 *
 * - an **intercept** listener that swallows Voice ([VoiceKeyConsumePolicy]) so the stock
 *   "voice commands not implemented" text never appears, and
 * - an **observing** listener for Phone, whose short press must still open the stock call screen.
 *
 * A long press ([SteeringLongPressPolicy]) runs [SteeringActionRunner]: bring the projection to the
 * front, then the assistant or Android Auto's phone screen.
 *
 * Registered for as long as OHU's service runs, not just during a session, so the text stays
 * hidden when nothing is projecting. Only while the user has turned it on: the receiver itself is
 * always in the list, and [onSettingsChanged] attaches and detaches it.
 */
class CarVoyahReceiver : CarKeyReceiver {

    override val isSupported = true
    override val isSUNeeded = false

    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var attached: Attached? = null

    /** Main thread only. */
    private val states = EnumMap<SteeringButton, SteeringLongPressPolicy.State>(SteeringButton::class.java)
    private val timers = EnumMap<SteeringButton, Runnable>(SteeringButton::class.java)

    @Volatile private var vtInstalled = false

    private class Attached(
        val manager: Any,
        val unregisters: List<Pair<Method, Any>>,
        val intercepting: Boolean,
    )

    override fun register(context: Context) {
        this.context = context.applicationContext
        attachIfWanted()
    }

    override fun unregister() {
        detach()
        context = null
    }

    override fun onSettingsChanged(context: Context) {
        if (this.context == null) return
        attachIfWanted()
    }

    private fun attachIfWanted() {
        val ctx = context ?: return
        val settingOn = App.provide(ctx).settings.voyahSteerEnabled
        val serviceVisible = VoyahKeyManagerSdk.isServiceInstalled(ctx)
        val wanted = settingOn && serviceVisible
        // Said every time: a silent "not wanted" left a car log with no way to tell which it was.
        AppLog.i("VoyahKeys: setting ${if (settingOn) "on" else "off"}, " +
            "${VoyahKeyManagerSdk.SERVICE_PACKAGE} ${if (serviceVisible) "visible" else "not visible"}, " +
            "currently ${if (attached != null) "attached" else "detached"}")
        if (wanted && attached == null && pending == null) attach(ctx)
        if (!wanted && (attached != null || pending != null)) detach()
    }

    private fun attach(ctx: Context) {
        vtInstalled = runCatching {
            ctx.packageManager.getApplicationInfo(EncarsManeuverPolicy.DEFAULT_TARGET_PACKAGE, 0)
        }.isSuccess
        AppLog.i("VoyahKeys: attaching (Voyah Tweaks ${if (vtInstalled) "installed" else "not installed"})")

        val loaded = VoyahKeyManagerSdk.load(ctx) ?: return
        VoyahKeyManagerSdk.dump(loaded)
        val described = VoyahKeyManagerSdk.describe(loaded.managerClass)
        val shape = KeyManagerApiShape.resolve(
            loaded.managerClass.name, described.constructors, described.methods.keys.toList(),
        )
        if (shape !is KeyManagerApiShape.Result.Usable) {
            AppLog.w("VoyahKeys: cannot use ${loaded.managerClass.name}: ${(shape as KeyManagerApiShape.Result.Refused).why}")
            return
        }

        try {
            val instance = shape.instance
            val initListener = initListenerFor(loaded.managerClass, instance)
            val args = instance.args.map {
                when (it) {
                    KeyManagerApiShape.Arg.Context -> ctx
                    KeyManagerApiShape.Arg.InitListener -> initListener
                    else -> null
                }
            }.toTypedArray()
            val manager = when (instance) {
                is KeyManagerApiShape.Instance.StaticFactory ->
                    described.methods.getValue(instance.method).invoke(null, *args)
                is KeyManagerApiShape.Instance.Constructor ->
                    loaded.managerClass.constructors.first { c ->
                        c.parameterTypes.map { it.name } == instance.params.map { it.name }
                    }.newInstance(*args)
            } ?: run {
                AppLog.w("VoyahKeys: ${loaded.managerClass.name} gave no instance")
                return
            }
            pending = Pending(manager, described, shape)
            if (initListener == null) {
                registerPending(ctx, "no init listener")
            } else {
                // The SDK binds to the service asynchronously and says so through the init listener.
                // A manager that was already bound may never say it again, so do not wait forever.
                main.postDelayed(initFallback, INIT_FALLBACK_MS)
            }
        } catch (e: Exception) {
            AppLog.e("VoyahKeys: creating the key manager failed", unwrap(e))
        }
    }

    /** A manager created but whose listeners wait for the SDK to say it is bound. Main thread. */
    private class Pending(
        val manager: Any,
        val described: VoyahKeyManagerSdk.Described,
        val shape: KeyManagerApiShape.Result.Usable,
    )

    private var pending: Pending? = null

    private val initFallback = Runnable { context?.let { registerPending(it, "no init result in ${INIT_FALLBACK_MS}ms") } }

    private fun initListenerFor(managerClass: Class<*>, instance: KeyManagerApiShape.Instance): Any? {
        if (KeyManagerApiShape.Arg.InitListener !in instance.args) return null
        val params = when (instance) {
            is KeyManagerApiShape.Instance.StaticFactory -> instance.method.params
            is KeyManagerApiShape.Instance.Constructor -> instance.params
        }
        val type = Class.forName(params[instance.args.indexOf(KeyManagerApiShape.Arg.InitListener)].name, false, managerClass.classLoader)
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "OHU Voyah init listener"
                else -> {
                    val ok = args?.firstOrNull() as? Boolean
                    AppLog.i("VoyahKeys: init ${method.name}(${args?.joinToString()})")
                    main.post {
                        val ctx = context
                        if (ok != false && ctx != null) registerPending(ctx, "init ${method.name}($ok)")
                    }
                    defaultFor(method.returnType)
                }
            }
        }
    }

    private fun registerPending(ctx: Context, why: String) {
        main.removeCallbacks(initFallback)
        val p = pending ?: return
        pending = null
        AppLog.i("VoyahKeys: registering listeners ($why)")
        try {
            val unregisters = mutableListOf<Pair<Method, Any>>()
            var intercepting = false
            p.shape.intercept?.let {
                unregisters += registerOne(ctx, p.manager, p.described, it, intercepting = true)
                intercepting = true
            }
            p.shape.observe?.let {
                unregisters += registerOne(ctx, p.manager, p.described, it, intercepting = false)
            }
            attached = Attached(p.manager, unregisters, intercepting)
            if (!intercepting) {
                AppLog.w("VoyahKeys: no intercept listener; Voice cannot be swallowed and the stock text will show")
            }
        } catch (e: Exception) {
            AppLog.e("VoyahKeys: registering with the key service failed", unwrap(e))
        }
    }

    /** Registers one listener; returns its unregister call, if the SDK has one. */
    private fun registerOne(
        ctx: Context,
        manager: Any,
        described: VoyahKeyManagerSdk.Described,
        reg: KeyManagerApiShape.Registration,
        intercepting: Boolean,
    ): List<Pair<Method, Any>> {
        val method = described.methods.getValue(reg.method)
        val listenerType = method.parameterTypes[reg.args.indexOf(KeyManagerApiShape.Arg.Listener)]
        val listener = Proxy.newProxyInstance(
            listenerType.classLoader, arrayOf(listenerType), Callback(intercepting),
        )
        val args = reg.args.map {
            when (it) {
                KeyManagerApiShape.Arg.Context -> ctx
                KeyManagerApiShape.Arg.Listener -> listener
                KeyManagerApiShape.Arg.InitListener -> null // never in a registration's args
                is KeyManagerApiShape.Arg.Priority -> it.value
            }
        }
        val result = method.invoke(manager, *args.toTypedArray())
        AppLog.i("VoyahKeys: ${reg.method.name}(${reg.args}) -> $result")
        return listOfNotNull(reg.unregister?.let { described.methods.getValue(it) to listener })
    }

    private fun detach() {
        main.removeCallbacks(initFallback)
        pending = null
        val current = attached ?: return
        attached = null
        for ((method, listener) in current.unregisters) {
            try {
                method.invoke(current.manager, listener)
            } catch (e: Exception) {
                AppLog.w("VoyahKeys: ${method.name} failed: ${unwrap(e)}")
            }
        }
        if (current.unregisters.isEmpty()) {
            AppLog.w("VoyahKeys: the SDK has no unregister call; listeners stay until OHU's process ends")
        }
        main.post {
            timers.values.forEach(main::removeCallbacks)
            timers.clear()
            states.clear()
        }
        AppLog.i("VoyahKeys: detached")
    }

    /**
     * One listener. Runs on whatever thread the SDK calls back on, and answers at once: whether to
     * consume is a function of the key code alone, and everything else is posted to main.
     */
    private inner class Callback(private val intercepting: Boolean) : InvocationHandler {
        private val unknownLogged = mutableSetOf<String>()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "hashCode" -> if (args.isNullOrEmpty()) return System.identityHashCode(proxy)
                "equals" -> if (args?.size == 1) return proxy === args[0]
                "toString" -> if (args.isNullOrEmpty()) return "OHU Voyah steering listener"
            }
            // The car's SDK calls back as (int code, KeyEvent). Which of the two carries the OEM
            // code (130/128) is not yet known, so the int wins when it names a button, and both
            // are logged.
            val event = args?.firstOrNull { it is KeyEvent } as KeyEvent?
            val intCode = args?.firstOrNull { it is Int } as Int?
            val code = intCode?.takeIf { SteeringLongPressPolicy.buttonFor(it) != null }
                ?: event?.keyCode ?: intCode
            if (code == null) {
                synchronized(unknownLogged) {
                    if (unknownLogged.add(method.name)) {
                        AppLog.i("VoyahKeys: callback ${method.name}(${args?.joinToString { it?.javaClass?.name ?: "null" }}) carries no key code")
                    }
                }
                return defaultFor(method.returnType)
            }
            val ctx = context ?: return defaultFor(method.returnType)
            val settings = App.provide(ctx).settings
            val kind = KeyManagerApiShape.callbackFor(method.name)
            val consume = intercepting && VoiceKeyConsumePolicy.consume(
                code, kind.longPhase, settings.voyahSteerEnabled, settings.voyahSteerVoiceLong, vtInstalled,
            )
            AppLog.d("VoyahKeys: ${if (intercepting) "intercept" else "observe"} ${method.name} ($kind) " +
                "int=$intCode keyCode=${event?.keyCode} action=${event?.action} repeat=${event?.repeatCount} " +
                "down=${event?.downTime} at=${event?.eventTime} consume=$consume")
            main.post { onKey(ctx, kind, code, event?.repeatCount ?: 0, fromIntercept = intercepting) }
            return if (method.returnType == Boolean::class.javaPrimitiveType) consume else defaultFor(method.returnType)
        }
    }

    private fun onKey(
        ctx: Context,
        kind: KeyManagerApiShape.Callback,
        code: Int,
        repeatCount: Int,
        fromIntercept: Boolean,
    ) {
        if (attached == null) return
        if (kind == KeyManagerApiShape.Callback.DOWN) {
            // Feed the keymap screen's debugger, so a button's code is visible from the app.
            ctx.sendBroadcast(Intent("com.andrerinas.openheadunit.DEBUG_KEY").apply {
                setPackage(ctx.packageName)
                putExtra("action", VoyahKeyManagerSdk.SERVICE_PACKAGE)
                putExtra("keyCode", code)
            })
        }

        val button = SteeringLongPressPolicy.buttonFor(code) ?: return
        // Voice belongs to the intercept listener when there is one, so a press both listeners
        // see is acted on once; Phone always to the observer, which is never allowed to consume.
        val owner = when (button) {
            SteeringButton.VOICE -> fromIntercept || attached?.intercepting == false
            SteeringButton.PHONE -> !fromIntercept
        }
        if (!owner) return
        val settings = App.provide(ctx).settings
        val enabled = when (button) {
            SteeringButton.VOICE -> settings.voyahSteerVoiceLong
            SteeringButton.PHONE -> settings.voyahSteerPhoneLong
        }
        if (!enabled) return

        val state = states[button] ?: SteeringLongPressPolicy.State()
        val decision = when (kind) {
            KeyManagerApiShape.Callback.DOWN -> SteeringLongPressPolicy.onDown(
                // Not vtInstalled: the car's SDK reports long presses itself (onKeyLongPressStart),
                // and firing on the first DOWN would take VT's short press if VT lets that DOWN by.
                state, button, repeatCount, longOnFirstDown = false, serviceReportsLong = true,
            )
            KeyManagerApiShape.Callback.LONG -> SteeringLongPressPolicy.onLongReported(state)
            KeyManagerApiShape.Callback.SHORT_END,
            KeyManagerApiShape.Callback.LONG_END -> SteeringLongPressPolicy.onUp(state)
            KeyManagerApiShape.Callback.HOLD,
            KeyManagerApiShape.Callback.OTHER -> return
        }
        apply(ctx, button, decision)
    }

    private fun apply(ctx: Context, button: SteeringButton, decision: SteeringLongPressPolicy.Decision) {
        states[button] = decision.state
        when (val effect = decision.effect) {
            SteeringLongPressPolicy.Effect.None -> Unit
            is SteeringLongPressPolicy.Effect.ArmTimer -> {
                timers.remove(button)?.let(main::removeCallbacks)
                val timer = Runnable {
                    timers.remove(button)
                    apply(ctx, button, SteeringLongPressPolicy.onTimer(states[button] ?: SteeringLongPressPolicy.State()))
                }
                timers[button] = timer
                main.postDelayed(timer, effect.delayMs)
            }
            SteeringLongPressPolicy.Effect.CancelTimer -> timers.remove(button)?.let(main::removeCallbacks)
            SteeringLongPressPolicy.Effect.Fire -> {
                timers.remove(button)?.let(main::removeCallbacks)
                AppLog.i("VoyahKeys: long press $button")
                SteeringActionRunner.run(ctx, button, "voyah-steer")
            }
        }
    }

    private fun defaultFor(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Short::class.javaPrimitiveType -> 0.toShort()
        Byte::class.javaPrimitiveType -> 0.toByte()
        Char::class.javaPrimitiveType -> 0.toChar()
        else -> null
    }

    private fun unwrap(e: Throwable): Throwable = (e as? InvocationTargetException)?.targetException ?: e

    private companion object {
        /** How long to wait for the SDK's init result before registering anyway. */
        const val INIT_FALLBACK_MS = 3000L
    }
}
