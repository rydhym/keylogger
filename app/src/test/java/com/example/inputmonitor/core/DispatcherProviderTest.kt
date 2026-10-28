package com.example.inputmonitor.core

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DispatcherProviderTest {

    @Test
    fun defaultDispatcherProvider_returnsStandardDispatchers() {
        val provider = DefaultDispatcherProvider()
        assertEquals(Dispatchers.Main, provider.main)
        assertEquals(Dispatchers.IO, provider.io)
        assertEquals(Dispatchers.Default, provider.default)
        assertEquals(Dispatchers.Unconfined, provider.unconfined)
    }

    @Test
    fun customDispatcherProvider_allowsOverride() {
        val testDispatcher = Dispatchers.Unconfined
        val customProvider = object : DispatcherProvider {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
            override val unconfined = testDispatcher
        }
        assertEquals(testDispatcher, customProvider.main)
        assertEquals(testDispatcher, customProvider.io)
        assertEquals(testDispatcher, customProvider.default)
        assertEquals(testDispatcher, customProvider.unconfined)
    }
}
