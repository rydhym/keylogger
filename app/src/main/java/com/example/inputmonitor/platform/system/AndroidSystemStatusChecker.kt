package com.example.inputmonitor.platform.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.example.inputmonitor.data.database.AppDatabase
import com.example.inputmonitor.platform.accessibility.AppAccessibilityService
import com.example.inputmonitor.platform.ime.CustomKeyboardService

/**
 * Android framework implementation of [SystemStatusChecker] querying system services
 * and secure settings (Architecture.md §14, SRS.md §13–§16).
 */
class AndroidSystemStatusChecker(
    private val context: Context,
    private val database: AppDatabase
) : SystemStatusChecker {

    override fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val isEnabledViaManager = enabledServices?.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                        serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == AppAccessibilityService::class.java.name
            } ?: false

            if (isEnabledViaManager) return true

            // Fallback: check Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES string
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val expectedShort = "${context.packageName}/.platform.accessibility.AppAccessibilityService"
            val expectedLong = "${context.packageName}/${AppAccessibilityService::class.java.name}"
            enabledServicesSetting.split(':').any {
                it.equals(expectedShort, ignoreCase = true) || it.equals(expectedLong, ignoreCase = true)
            }
        } catch (_: Throwable) {
            false
        }
    }

    override fun isImeInstalled(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.inputMethodList?.any { it.packageName == context.packageName } ?: true
        } catch (_: Throwable) {
            true // If query fails, assume installed since declared in manifest
        }
    }

    override fun isImeEnabled(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.any { it.packageName == context.packageName } ?: false
        } catch (_: Throwable) {
            false
        }
    }

    override fun isImeSelected(): Boolean {
        return try {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false

            val expectedShort = "${context.packageName}/.platform.ime.CustomKeyboardService"
            val expectedLong = "${context.packageName}/${CustomKeyboardService::class.java.name}"
            defaultIme.equals(expectedShort, ignoreCase = true) ||
                    defaultIme.equals(expectedLong, ignoreCase = true) ||
                    defaultIme.startsWith(context.packageName)
        } catch (_: Throwable) {
            false
        }
    }

    override fun isDatabaseReady(): Boolean {
        return try {
            database.openHelper.writableDatabase
            true
        } catch (_: Throwable) {
            false
        }
    }
}
