package com.sevtinge.hyperceiler.libhook.corepatch

import android.annotation.SuppressLint
import android.os.Build
import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hookBefore
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object AssetManagerHook : BaseHook() {
    override val name = "AssetManagerHook"

    @SuppressLint("BlockedPrivateApi")
    override fun hook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val assetManagerClazz = hostClassLoader.loadClass("android.content.res.AssetManager")

        // Targeting R+ (version " + Build.VERSION_CODES.R + " and above) requires"
        // + " the resources.arsc of installed APKs to be stored uncompressed"
        // + " and aligned on a 4-byte boundary
        // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r48:frameworks/base/core/java/android/content/res/AssetManager.java;l=828
        // public boolean containsAllocatedTable()
        val containsAllocatedTableMethod =
            assetManagerClazz.getDeclaredMethod("containsAllocatedTable")
        hookBefore(containsAllocatedTableMethod) { callback ->
            if (Config.isBypassResourceArscRestrictionsEnabled()) {
                callback.returnAndSkip(false)
            }
        }
    }
}
