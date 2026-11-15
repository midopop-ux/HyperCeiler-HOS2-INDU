package com.sevtinge.hyperceiler.libhook.corepatch

import android.annotation.SuppressLint
import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hookBefore
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object ApkSigningBlockUtilsHook : BaseHook() {
    override val name = "ApkSigningBlockUtilsHook"

    @SuppressLint("PrivateApi")
    override fun hook() {
        val apkSigningBlockUtilsClazz =
            hostClassLoader.loadClass("android.util.apk.ApkSigningBlockUtils")
        // https://cs.android.com/android/platform/superproject/+/android-9.0.0_r61:frameworks/base/core/java/android/util/apk/ApkSigningBlockUtils.java;l=303
        val parseVerityDigestAndVerifySourceLengthMethod =
            apkSigningBlockUtilsClazz.declaredMethods.first { m -> m.name == "parseVerityDigestAndVerifySourceLength" }
        hookBefore(parseVerityDigestAndVerifySourceLengthMethod) { callback ->
            if (Config.isBypassVerificationEnabled()) {
                callback.returnAndSkip((callback.args[0] as ByteArray).copyOfRange(0, 32))
            }
        }

        val verifyIntegrityForVerityBasedAlgorithmMethod =
            apkSigningBlockUtilsClazz.declaredMethods.first { m -> m.name == "verifyIntegrityForVerityBasedAlgorithm" }
        hookBefore(verifyIntegrityForVerityBasedAlgorithmMethod) { callback ->
            if (Config.isBypassVerificationEnabled()) {
                callback.returnAndSkip(null)
            }
        }
    }
}
