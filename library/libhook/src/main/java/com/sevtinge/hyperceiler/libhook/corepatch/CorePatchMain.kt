package com.sevtinge.hyperceiler.libhook.corepatch

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

object CorePatchMain {

    @JvmStatic
    fun onModuleLoaded(module: XposedModule) {
        XposedHelper.setXposedModule(module)
    }

    @JvmStatic
    fun onSystemServerStarting(module: XposedModule, param: XposedModuleInterface.SystemServerStartingParam) {
        XposedHelper.setXposedModule(module)
        XposedHelper.setHostClassLoader(param.classLoader)
        XposedHelper.log("CorePatchMain: Starting hooks on SDK ${Build.VERSION.SDK_INT}")

        val hooks = listOf(
            ApkSignatureVerifierHook,
            ApkSigningBlockUtilsHook,
            ApplicationInfoHook,
            AssetManagerHook,
            FrameworkParsingPackageUtilsHook,
            InstallPackageHelperHook,
            KeySetManagerServiceHook,
            MessageDigestHook,
            NtConfigListServiceImplHook,
            PackageInstallerSessionHook,
            PackageManagerServiceHook,
            PackageManagerServiceUtilsHook,
            ReconcilePackageUtilsHook,
            ScanPackageUtilsHook,
            SharedUserSettingHook,
            SigningDetailsHook,
            StrictJarVerifierHook,
            VerificationParamsHook,
            VerifyingSessionHook,
        )
        hooks.forEach {
            try {
                it.init()
            } catch (t: Throwable) {
                XposedHelper.log("Hook ${it.name} failed", t)
            }
        }
    }
}
