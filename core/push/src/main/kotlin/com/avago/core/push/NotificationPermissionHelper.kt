package com.avago.core.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility for checking and requesting the Android 13+ POST_NOTIFICATIONS permission.
 *
 * Usage (from an Activity or Fragment):
 * ```
 * if (NotificationPermissionHelper.shouldRequest(context)) {
 *     requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
 * }
 * ```
 */
object NotificationPermissionHelper {

    /**
     * Returns `true` when the app is running on Android 13+ and the
     * POST_NOTIFICATIONS permission has not yet been granted.
     */
    fun shouldRequest(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns `true` when the app currently holds the POST_NOTIFICATIONS permission
     * (always `true` below Android 13 where the permission does not exist).
     */
    fun isGranted(context: Context): Boolean = !shouldRequest(context)
}
