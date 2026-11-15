package com.sevtinge.hyperceiler.libhook.corepatch

import com.sevtinge.hyperceiler.libhook.corepatch.CorePatchConfig as Config
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hookBefore
import com.sevtinge.hyperceiler.libhook.corepatch.XposedHelper.hostClassLoader

object NtConfigListServiceImplHook : BaseHook() {
    override val name = "NtConfigListServiceImplHook"

    override fun hook() {
        // TODO: Check is Nothing Phone
        val ntConfigListServiceImplClazz = try {
            hostClassLoader.loadClass("com.nothing.server.ex.NtConfigListServiceImpl")
        } catch (e: ClassNotFoundException) {
            return
        }
        val isInstallingAppForbiddenMethod =
            ntConfigListServiceImplClazz.declaredMethods.first { m -> m.name == "isInstallingAppForbidden" }
        hookBefore(isInstallingAppForbiddenMethod) { callback ->
            if (Config.isBypassBlockEnabled()) callback.returnAndSkip(false)
        }
        val isStartingAppForbiddenMethod =
            ntConfigListServiceImplClazz.declaredMethods.first { m -> m.name == "isStartingAppForbidden" }
        hookBefore(isStartingAppForbiddenMethod) { callback ->
            if (Config.isBypassBlockEnabled()) callback.returnAndSkip(false)
        }
    }
}
