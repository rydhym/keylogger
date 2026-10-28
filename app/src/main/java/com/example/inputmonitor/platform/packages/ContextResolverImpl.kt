package com.example.inputmonitor.platform.packages

import android.content.Context
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.context.ApplicationContextInfo
import com.example.inputmonitor.domain.context.ContextResolver
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [ContextResolver] with in-memory caching (Architecture.md §41, §42).
 *
 * Avoids repeated expensive Android PackageManager lookups during rapid key typing.
 */
class ContextResolverImpl(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val packageLabelProvider: (String) -> String = { pkg ->
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }
) : ContextResolver {

    private val labelCache = ConcurrentHashMap<String, String>()

    override suspend fun resolve(packageName: String?): ApplicationContextInfo {
        if (packageName.isNullOrBlank()) {
            return ApplicationContextInfo(packageName = null, applicationLabel = null)
        }

        // Fast-path: return from memory cache without dispatcher switch or IPC
        labelCache[packageName]?.let { cachedLabel ->
            return ApplicationContextInfo(packageName = packageName, applicationLabel = cachedLabel)
        }

        // Slow-path: query package label provider on IO thread and cache result
        return withContext(dispatchers.io) {
            val label = try {
                packageLabelProvider(packageName)
            } catch (_: Exception) {
                packageName
            }

            if (labelCache.size >= MAX_CACHE_ENTRIES) {
                val oldest = labelCache.keys.firstOrNull()
                if (oldest != null) {
                    labelCache.remove(oldest)
                }
            }
            labelCache[packageName] = label
            ApplicationContextInfo(packageName = packageName, applicationLabel = label)
        }
    }

    companion object {
        const val MAX_CACHE_ENTRIES = 256
    }
}
