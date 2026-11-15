package com.sevtinge.hyperceiler.libhook.corepatch

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hookBefore
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object ApplicationInfoHook : BaseHook() {
    override val name = "ApplicationInfoHook"

    @SuppressLint("SoonBlockedPrivateApi")
    override fun hook() {
        val applicationInfoClazz = hostClassLoader.loadClass("android.content.pm.ApplicationInfo")

        // if app is system app, allow to use hidden api, even if app not using a system signature
        // https://cs.android.com/android/platform/superproject/+/android-9.0.0_r61:frameworks/base/core/java/android/content/pm/ApplicationInfo.java;l=1678
        // private boolean isPackageWhitelistedForHiddenApis()
        val isPackageWhitelistedForHiddenApisMethod =
            applicationInfoClazz.getDeclaredMethod("isPackageWhitelistedForHiddenApis")
        hookBefore(isPackageWhitelistedForHiddenApisMethod) { callback ->
            if (Config.isAllowHiddenApisForSystemAppsEnabled()) {
                val applicationInfo = callback.thisObject as ApplicationInfo
                if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 || applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) {
                    callback.returnAndSkip(true)
                }
            }
        }
    }
}
