package com.sevtinge.hyperceiler.libhook.corepatch

import android.annotation.SuppressLint
import android.os.Build
import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hookBefore
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object VerificationParamsHook: BaseHook() {
    override val name = "VerificationParamsHook"

    @SuppressLint("PrivateApi")
    override fun hook() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val verificationParamsClazz = hostClassLoader.loadClass("com.android.server.pm.VerificationParams")

        val isVerificationEnabledMethod = verificationParamsClazz.declaredMethods.first { m -> m.name == "isVerificationEnabled" }
        hookBefore(isVerificationEnabledMethod) { callback ->
            if (Config.isDisableVerificationAgentEnabled()) {
                callback.returnAndSkip(false)
            }
        }
    }
}
