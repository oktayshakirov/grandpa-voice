package com.grandpavoice.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.grandpavoice.ui.MainActivity

/**
 * Optional dedicated-device ("kiosk") mode. Works only if the app was made device owner with
 *   adb shell dpm set-device-owner com.grandpavoice/.kiosk.AdminReceiver
 * Without that, every function here is a harmless no-op.
 */
object Kiosk {

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)
    private fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context)?.isDeviceOwnerApp(context.packageName) == true

    fun isInLockTask(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun applyPolicies(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val dpm = dpm(activity) ?: return
        val admin = admin(activity)
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(activity.packageName)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Keep the power menu so the phone can still be switched off; hide everything else.
            runCatching { dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) }
        }
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching { dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java)) }
        runCatching { dpm.setKeyguardDisabled(admin, true) }
        runCatching { dpm.setStatusBarDisabled(admin, true) }
    }

    fun enterIfAllowed(activity: Activity) {
        if (!isDeviceOwner(activity) || isInLockTask(activity)) return
        runCatching { activity.startLockTask() }
    }

    fun exitTemporarily(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    /** Fully undoes device-owner mode so the app can be uninstalled normally. */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val dpm = dpm(activity) ?: return
        val admin = admin(activity)
        runCatching { activity.stopLockTask() }
        runCatching { dpm.setStatusBarDisabled(admin, false) }
        runCatching { dpm.setKeyguardDisabled(admin, false) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
        runCatching { dpm.clearDeviceOwnerApp(activity.packageName) }
    }
}
