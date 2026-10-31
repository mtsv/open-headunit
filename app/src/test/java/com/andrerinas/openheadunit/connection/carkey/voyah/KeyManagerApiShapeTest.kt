package com.andrerinas.openheadunit.connection.carkey.voyah

import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.Arg
import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.Callback
import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.Instance
import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.MethodRef
import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.Result
import com.andrerinas.openheadunit.connection.carkey.voyah.KeyManagerApiShape.TypeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what is accepted from the Voyah key-manager SDK and, as importantly, what is refused rather
 * than guessed at. The first table is the SDK as dumped from the car on 2026-10-31 (alpha-10), which
 * the earlier guess refused for want of a bare `getInstance(Context)`.
 */
class KeyManagerApiShapeTest {

    private val manager = KeyManagerApiShape.MANAGER_CLASS
    private val context = TypeRef("android.content.Context")
    private val int = TypeRef("int")
    private val intArray = TypeRef("[I")
    private val string = TypeRef("java.lang.String")
    private val listener = TypeRef("com.qinggan.system.KeyManager\$OnKeyEventListener", isInterface = true)
    private val init = TypeRef("com.qinggan.system.KeyManager\$OnInitListener", isInterface = true)
    private val stub = TypeRef("com.qinggan.system.IKeyManagerCallback\$Stub", isInterface = false)
    private val getInstance = MethodRef("getInstance", listOf(context), returns = manager, isStatic = true)

    private fun resolve(vararg methods: MethodRef, constructors: List<List<TypeRef>> = emptyList()) =
        KeyManagerApiShape.resolve(manager, constructors, methods.toList())

    private fun m(name: String, vararg params: TypeRef) = MethodRef(name, params.toList(), returns = "int")

    // --- the car's SDK ---

    private val carGetInstance = MethodRef("getInstance", listOf(context, init), returns = manager, isStatic = true)
    private val carGetInstance3 = MethodRef("getInstance", listOf(context, init, int), returns = manager, isStatic = true)
    private val carIntercept = m("registerInterceptListener", listener, int)
    private val carUnIntercept = m("unregisterInterceptListener", listener)
    private val carObserve = m("registerKeyEventListener", listener)
    private val carUnObserve = m("unregisterKeyEventListener", listener)
    private val carMethods = arrayOf(
        carGetInstance, carGetInstance3,
        m("registerInterceptListener", listener),
        carIntercept,
        m("registerInterceptListener", listener, intArray),
        m("registerInterceptListener", string, listener),
        m("registerInterceptListener", listener, intArray, int),
        m("registerInterceptListener", string, listener, intArray),
        carObserve,
        m("registerKeyEventListener", listener, intArray),
        m("registerKeyEventListener", string, listener),
        m("registerKeyEventListener", string, listener, intArray),
        carUnIntercept, m("unregisterInterceptListener", string, listener),
        carUnObserve, m("unregisterKeyEventListener", string, listener),
    )

    @Test
    fun `the car's SDK is usable - init-listener factory, prioritised intercept, plain observer`() {
        val result = resolve(*carMethods, constructors = listOf(listOf(context, init))) as Result.Usable

        assertEquals(Instance.StaticFactory(carGetInstance, listOf(Arg.Context, Arg.InitListener)), result.instance)
        assertEquals(carIntercept, result.intercept!!.method)
        assertEquals(listOf(Arg.Listener, Arg.Priority(KeyManagerApiShape.INTERCEPT_PRIORITY)), result.intercept!!.args)
        assertEquals(carUnIntercept, result.intercept!!.unregister)
        assertEquals(carObserve, result.observe!!.method)
        assertEquals(listOf(Arg.Listener), result.observe!!.args)
        assertEquals(carUnObserve, result.observe!!.unregister)
    }

    @Test
    fun `the car's init-listener constructor serves when there is no getInstance`() {
        val result = resolve(carIntercept, constructors = listOf(listOf(context, init))) as Result.Usable
        assertEquals(Instance.Constructor(listOf(context, init), listOf(Arg.Context, Arg.InitListener)), result.instance)
    }

    @Test
    fun `an init listener is preferred over a bare Context, since it says when registering works`() {
        val result = resolve(getInstance, carGetInstance, carIntercept) as Result.Usable
        assertEquals(carGetInstance, (result.instance as Instance.StaticFactory).method)
    }

    @Test
    fun `the init listener is never taken for the key listener`() {
        val intercept = m("registerInterceptListener", init, int)
        assertTrue(resolve(getInstance, intercept) is Result.Refused)
    }

    @Test
    fun `the listener callbacks the car's SDK declares are classed`() {
        assertEquals(Callback.DOWN, KeyManagerApiShape.callbackFor("onKeyDown"))
        assertEquals(Callback.LONG, KeyManagerApiShape.callbackFor("onKeyLongPressStart"))
        assertEquals(Callback.SHORT_END, KeyManagerApiShape.callbackFor("onKeyShortClick"))
        assertEquals(Callback.LONG_END, KeyManagerApiShape.callbackFor("onKeyLongPressStop"))
        assertEquals(Callback.LONG_END, KeyManagerApiShape.callbackFor("onKeyHoldPressStop"))
        assertEquals(Callback.HOLD, KeyManagerApiShape.callbackFor("onKeyHoldPressStart"))
        assertEquals(Callback.OTHER, KeyManagerApiShape.callbackFor("somethingNew"))
    }

    @Test
    fun `only the callbacks a long press alone produces are long-phase`() {
        val long = Callback.values().filter { it.longPhase }.toSet()
        assertEquals(setOf(Callback.HOLD, Callback.LONG, Callback.LONG_END), long)
    }

    // --- general rules ---

    @Test
    fun `the intercept priority sits above power manager and below the login screen and IME`() {
        assertTrue(KeyManagerApiShape.INTERCEPT_PRIORITY > 100)
        assertTrue(KeyManagerApiShape.INTERCEPT_PRIORITY < Int.MAX_VALUE)
    }

    @Test
    fun `a bare getInstance(Context) still serves`() {
        val result = resolve(getInstance, carIntercept) as Result.Usable
        assertEquals(Instance.StaticFactory(getInstance, listOf(Arg.Context)), result.instance)
    }

    @Test
    fun `a Context parameter is filled, in any position`() {
        val intercept = MethodRef("registerInterceptListener", listOf(context, int, listener))
        val result = resolve(getInstance, intercept) as Result.Usable
        assertEquals(listOf(Arg.Context, Arg.Priority(200), Arg.Listener), result.intercept!!.args)
        assertNull(result.observe)
    }

    @Test
    fun `an abstract AIDL stub cannot be proxied, so it is refused`() {
        val intercept = MethodRef("registerInterceptListener", listOf(stub, int))
        assertTrue(resolve(getInstance, intercept) is Result.Refused)
    }

    @Test
    fun `an AIDL callback interface is refused, since a proxy has no binder to hand over`() {
        val aidl = TypeRef("com.qinggan.system.IKeyManagerCallback", isInterface = true, isBinder = true)
        val intercept = MethodRef("registerInterceptListener", listOf(aidl, int))
        assertTrue(resolve(getInstance, intercept) is Result.Refused)
    }

    @Test
    fun `an unknown parameter type is refused rather than guessed`() {
        val intercept = MethodRef("registerInterceptListener", listOf(listener, string))
        assertTrue(resolve(getInstance, intercept) is Result.Refused)
    }

    @Test
    fun `two overloads that fit equally well are ambiguous and skipped`() {
        val other = TypeRef("com.qinggan.system.OtherListener", isInterface = true)
        val a = MethodRef("registerInterceptListener", listOf(listener, int))
        val b = MethodRef("registerInterceptListener", listOf(other, int))
        val result = resolve(getInstance, a, b, carObserve) as Result.Usable
        assertNull(result.intercept)
        assertEquals(carObserve, result.observe!!.method)
    }

    @Test
    fun `no way to get an instance is refused`() {
        assertTrue(resolve(carIntercept) is Result.Refused)
    }

    @Test
    fun `a static or misnamed register method does not count`() {
        val static = MethodRef("registerInterceptListener", listOf(listener, int), isStatic = true)
        val other = MethodRef("addListener", listOf(listener, int))
        assertTrue(resolve(getInstance, static, other) is Result.Refused)
    }
}
