package com.geoquiz.app.ui.whatsnew

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether this install was updated from an earlier version, one of the two signals
 * [WhatsNewViewModel] uses to tell a genuine fresh install apart from an existing player
 * upgrading into a build that introduces the `whats_new_seen_version` setting for the first time
 * (P10-F). A small interface so the real [PackageManager] lookup can be swapped for a fake in
 * tests.
 */
interface AppInstallInfo {
    /** True when [PackageManager]'s `firstInstallTime` is before its `lastUpdateTime`. */
    val wasUpdated: Boolean
}

/** Reads [wasUpdated] from the real [PackageManager]. */
class PackageManagerAppInstallInfo @Inject constructor(
    @ApplicationContext private val context: Context
) : AppInstallInfo {

    override val wasUpdated: Boolean
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime < info.lastUpdateTime
        }.getOrDefault(false)
}
