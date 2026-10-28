package com.example.inputmonitor.platform.packages

import com.example.inputmonitor.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ContextResolverImpl] (Architecture.md §41, §42, TESTING.md §4).
 */
class ContextResolverTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private var labelLookupCallCount = 0
    private val packageLabels = mutableMapOf<String, String>()
    private val failingPackages = mutableSetOf<String>()

    private lateinit var contextResolver: ContextResolverImpl

    @Before
    fun setUp() {
        labelLookupCallCount = 0
        packageLabels.clear()
        failingPackages.clear()

        val dummyContext = object : android.content.ContextWrapper(null) {}

        contextResolver = ContextResolverImpl(
            context = dummyContext,
            dispatchers = testDispatchers,
            packageLabelProvider = { pkg ->
                labelLookupCallCount++
                if (failingPackages.contains(pkg)) {
                    throw RuntimeException("Package lookup failed: $pkg")
                }
                packageLabels[pkg] ?: pkg
            }
        )
    }

    @Test
    fun resolve_nullOrBlankPackage_returnsNullsWithoutCallingProvider() {
        runBlocking {
            val nullResult = contextResolver.resolve(null)
            assertNull(nullResult.packageName)
            assertNull(nullResult.applicationLabel)

            val emptyResult = contextResolver.resolve("")
            assertNull(emptyResult.packageName)
            assertNull(emptyResult.applicationLabel)

            val blankResult = contextResolver.resolve("   ")
            assertNull(blankResult.packageName)
            assertNull(blankResult.applicationLabel)

            assertEquals(0, labelLookupCallCount)
        }
    }

    @Test
    fun resolve_validPackage_resolvesHumanReadableLabel() {
        runBlocking {
            val packageName = "com.example.browser"
            val expectedLabel = "Example Browser"
            packageLabels[packageName] = expectedLabel

            val result = contextResolver.resolve(packageName)

            assertEquals(packageName, result.packageName)
            assertEquals(expectedLabel, result.applicationLabel)
            assertEquals(1, labelLookupCallCount)
        }
    }

    @Test
    fun resolve_cachedPackage_returnsFromCacheWithoutCallingProviderAgain() {
        runBlocking {
            val packageName = "com.example.chat"
            val expectedLabel = "Chat App"
            packageLabels[packageName] = expectedLabel

            // First resolution: slow path (queries provider)
            val firstResult = contextResolver.resolve(packageName)
            assertEquals(expectedLabel, firstResult.applicationLabel)
            assertEquals(1, labelLookupCallCount)

            // Second resolution: fast path (must be returned from memory cache)
            val secondResult = contextResolver.resolve(packageName)
            assertEquals(expectedLabel, secondResult.applicationLabel)

            // Verify provider was not called again
            assertEquals(1, labelLookupCallCount)
        }
    }

    @Test
    fun resolve_uninstalledOrFailingPackage_fallsBackToPackageNameSafely() {
        runBlocking {
            val packageName = "com.unknown.orphan"
            failingPackages.add(packageName)

            val result = contextResolver.resolve(packageName)

            assertEquals(packageName, result.packageName)
            assertEquals(packageName, result.applicationLabel)
            assertEquals(1, labelLookupCallCount)
        }
    }

    @Test
    fun resolve_multipleDifferentPackages_cachesIndependently() {
        runBlocking {
            packageLabels["com.app.one"] = "App One"
            packageLabels["com.app.two"] = "App Two"

            val res1 = contextResolver.resolve("com.app.one")
            val res2 = contextResolver.resolve("com.app.two")

            assertEquals("App One", res1.applicationLabel)
            assertEquals("App Two", res2.applicationLabel)
            assertEquals(2, labelLookupCallCount)

            // Cache hits
            contextResolver.resolve("com.app.one")
            contextResolver.resolve("com.app.two")
            assertEquals(2, labelLookupCallCount)
        }
    }
}
