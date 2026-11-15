package com.sevtinge.hyperceiler.libhook.corepatch

import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object MessageDigestHook : BaseHook() {
    override val name = "MessageDigestHook"

    override fun hook() {
        val messageDigestClazz = hostClassLoader.loadClass("java.security.MessageDigest")
        // https://cs.android.com/android/platform/superproject/main/+/main:libcore/ojluni/src/main/java/java/security/MessageDigest.java;l=518
        // public static boolean isEqual(byte[] digesta, byte[] digestb)
        val isEqualMethod = messageDigestClazz.getDeclaredMethod(
            "isEqual", ByteArray::class.java, ByteArray::class.java
        )
        XposedHelper.hookBefore(isEqualMethod) { callback ->
            if (Config.isBypassVerificationEnabled()) {
                callback.returnAndSkip(true)
            }
        }
    }
}
