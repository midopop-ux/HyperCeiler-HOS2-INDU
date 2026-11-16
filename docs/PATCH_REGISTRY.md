# AGENT INTERNAL PATCH REGISTRY & SMALI-TO-SOURCE REVERSE COMPILER DIRECTIVES
# Target Project: HyperCeiler-HOS2
# Agent Role: Autonomous Patch Translator & Upstream Synchronizer
# Timestamp Standard: Device Local Time (EET / Asia-Cairo)

[AGENT_DIRECTIVE]
Whenever user states: "تمام إحنا نجحنا بأكواد سمايلي اللي معانا، عايزك بقى تطبق الكلام ده على السورس اللي على جيت هاب":
1. Read this file immediately to retrieve all active bytecode modifications.
2. For each registered patch, inspect the corresponding Target Source File.
3. Verify signature matching, hook lifecycle in XposedInitEntry, and preference key mappings.
4. Execute exact Java/Kotlin implementation, compile verification, and push cleanly to main.

Whenever user queries: "كل التعديلات اللي عملناها آخر ساعتين، ابعتها":
- Parse all [TIMESTAMP] headers below and return the patches registered within the requested window.

================================================================================
MODULE 1: CorePatch Modern Integration (v4.5 Engine)
Source Origin: org.lsposed.corepatch / Root DEX classes (a.smali .. r0.smali)
Target Source Package: com.sevtinge.hyperceiler.libhook.corepatch.*
UI Destination: com.sevtinge.hyperceiler.hooker.framework.CorePatchSettings.java
Bootstrap Hook: com.sevtinge.hyperceiler.libhook.base.XposedInitEntry.java
Timestamp Registered: 2026-11-16 05:05:00 EET
================================================================================

--- [PATCH: CP-01] BYPASS DOWNGRADE ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/PackageManagerService;->checkDowngrade (Android <= 12 / SDK <= 32)
  * Lcom/android/server/pm/PackageManagerServiceUtils;->checkDowngrade (Android >= 13 / SDK >= 33)
  * Flyme 9: Lcom/android/server/pm/PackageManagerService;->checkDowngrade (return boolean)
- Logic Inversion:
  * On SDK <= 29 (Q and below): zero out reflection fields 'mVersionCode' and 'mVersionCodeMajor' on beforePackage object.
  * Return/skip with null for void methods, return true for boolean methods.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/PackageManagerServiceHook.kt
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/PackageManagerServiceUtilsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_downgr

--- [PATCH: CP-02] BYPASS VERIFICATION & DIGEST ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/util/apk/ApkSignatureVerifier;->verify / verifyV1Signature / verifyV2Signature / verifyV3Signature
  * Landroid/util/apk/ApkSigningBlockUtils;->verifySignatureDigest
  * Lcom/android/server/pm/PackageManagerServiceUtils;->verifySignatures
  * Lcom/android/server/pm/ScanPackageUtils;->assertPackageIsValid
  * Ljava/security/MessageDigest;->isEqual([B[B)Z
  * Lcom/android/server/pm/StrictJarVerifier;->verify / readFullyAtOffset
- Logic Inversion:
  * ApkSignatureVerifier: return dummy constant signature (Constant.SIGNATURE).
  * ApkSigningBlockUtils: slice byte array to first 32 bytes to fake digest match.
  * PackageManagerServiceUtils.verifySignatures: return false (no mismatch found).
  * MessageDigest.isEqual: return true.
  * ScanPackageUtils.assertPackageIsValid: skip exception throwing (return null).
- Kotlin Implementation:
  * Target Files: ApkSignatureVerifierHook.kt, ApkSigningBlockUtilsHook.kt, PackageManagerServiceUtilsHook.kt, MessageDigestHook.kt, ScanPackageUtilsHook.kt, StrictJarVerifierHook.kt
- Pref Key: prefs_key_system_framework_core_patch_auth_creak

--- [PATCH: CP-03] BYPASS RESOURCES.ARSC 4-BYTE ALIGNMENT ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/res/AssetManager;->nativeLoadResourceBag / loadApkAssets
  * Landroid/content/res/ApkAssets;->loadFromPath
- Logic Inversion:
  * Force bypass 4-byte alignment restriction for uncompressed resources.arsc on Android 11+.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/AssetManagerHook.kt
- Pref Key: prefs_key_system_framework_bypass_resource_arsc

--- [PATCH: CP-04] BYPASS DIGEST & SIGNATURE LINEAGE ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/pm/PackageParser$SigningDetails;->checkCapability
  * Landroid/content/pm/SigningDetails;->hasCommonAncestor
- Logic Inversion:
  * Force hasCommonAncestor to return true when verifySignatures is in current stack trace.
  * Bypass signature lineage divergence errors.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/SigningDetailsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_digest_creak

--- [PATCH: CP-05] BYPASS EXACT SIGNATURE MATCH ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/pm/PackageParser$SigningDetails;->signaturesMatchExactly
  * Landroid/content/pm/SigningDetails;->signaturesMatchExactly
- Logic Inversion:
  * Intercept signaturesMatchExactly and return true unconditionally. Allows split APKs with different certs.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/SigningDetailsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_exact_signature_check

--- [PATCH: CP-06] USE PREVIOUS SIGNATURES (DANGEROUS) ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/InstallPackageHelper;->checkPackageSignature
  * Lcom/android/server/pm/PackageManagerService;->doesSignatureMatchForPermissions
- Logic Inversion:
  * When package names match between incoming and existing app, override callback result to true.
  * UI includes explicit safety warning dialog for Xiaomi/Redmi HyperOS devices.
- Kotlin & Java Implementation:
  * Target Files: InstallPackageHelperHook.kt, PackageManagerServiceHook.kt, CorePatchSettings.java
- Pref Key: prefs_key_system_framework_core_patch_use_pre_signature

--- [PATCH: CP-07] ALLOW HIDDEN APIS FOR SYSTEM APPS ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/pm/ApplicationInfo;->isAllowedToUseHiddenApis
- Logic Inversion:
  * Return true if app is system or matches target criteria, granting unrestricted hidden API access.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/ApplicationInfoHook.kt
- Pref Key: prefs_key_system_framework_allow_hidden_apis

--- [PATCH: CP-08] BYPASS SHARED USER SIGNATURE MATCH ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/SharedUserSetting;->signaturesMatch
  * Lcom/android/server/pm/ReconcilePackageUtils;->reconcilePackages
- Logic Inversion:
  * Allow packages sharing the same sharedUserId to install even if signing certs differ.
- Kotlin Implementation:
  * Target Files: SharedUserSettingHook.kt, ReconcilePackageUtilsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_shared_user

--- [PATCH: CP-09] DISABLE PACKAGE VERIFICATION AGENT ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/PackageManagerService;->isVerificationEnabled
  * Lcom/android/server/pm/VerificationParams;->getVerificationState
  * Lcom/android/server/pm/VerifyingSession;->handleStartCopy
- Logic Inversion:
  * Intercept isVerificationEnabled and return false.
  * Strip Google Play Protect and external verification agents from package install flow.
- Kotlin Implementation:
  * Target Files: PackageManagerServiceHook.kt, VerificationParamsHook.kt, VerifyingSessionHook.kt
- Pref Key: prefs_key_system_framework_disable_verification_agent

--- [PATCH: CP-10] BYPASS OEM INSTALLATION BLOCKLIST ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/NtConfigListServiceImpl;->isPackageInBlockList
- Logic Inversion:
  * Return false for blocklist checks (specifically for Nothing OS and OEM-specific installation blacklists).
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/NtConfigListServiceImplHook.kt
- Pref Key: prefs_key_system_framework_bypass_block

================================================================================
BOOTSTRAP & LIFECYCLE WIRING DIRECTIVES
================================================================================
[TIMESTAMP]: 2026-11-16 05:13:00 EET
1. Hook Lifecycle Entry:
   - File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.java
   - In onModuleLoaded: must call `com.sevtinge.hyperceiler.libhook.corepatch.CorePatchMain.onModuleLoaded(this);`
   - In onSystemServerStarting: must call `com.sevtinge.hyperceiler.libhook.corepatch.CorePatchMain.onSystemServerStarting(this, lpparam);`
   - STRICT PROHIBITION: Never call legacy `new CorePatch().onLoad(lpparam)` to avoid dual PMS hook collisions.

2. R8 / ProGuard Keep Directives:
   - File: library/libhook/src/main/keepRules/rules.keep
   - Must contain: `-keep class com.sevtinge.hyperceiler.libhook.corepatch.** { *; }`

================================================================================
MODULE 2: Navigation Bar 3-Button Long Press Custom Actions
Source Origin: Pengeek H2 / ij.smali, u8.smali, pf.smali
Target Source Package: com.sevtinge.hyperceiler.libhook.rules.systemframework.navbar.*
UI Destination: library/core/src/main/res/xml/system_ui_navigation.xml
Hook Registration: SystemFrameworkV.java & SystemFrameworkB.java
Timestamp Registered: 2026-11-16 07:40:00 EET
================================================================================

--- [PATCH: NB-01] 3-BUTTON LONG PRESS ACTION INTERCEPTION & SYSTEM OVERRIDE ---
[TIMESTAMP]: 2026-11-16 07:40:00 EET
- Smali Target:
  * Lcom/android/server/policy/MiuiSingleKeyRule;->getFunction(Ljava/lang/String;)Ljava/lang/String;
  * Lcom/miui/server/input/util/ShortCutActionsUtils;->triggerFunction(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;Z)Z
- Logic & Precedence:
  * In `MiuiSingleKeyRule.getFunction(action)`:
    - If `action == "long_press_back_key"` and `prefs_key_navigation_bar_back_long_press_action > 0`, return `"custom_nav_back_long"`.
    - If `action == "long_press_home_key"` and `prefs_key_navigation_bar_home_long_press_action > 0`, return `"custom_nav_home_long"`.
    - If `action == "long_press_menu_key"` and `prefs_key_navigation_bar_menu_long_press_action > 0`, return `"custom_nav_menu_long"`.
  * In `ShortCutActionsUtils.triggerFunction(action, ...)`:
    - Intercept `"custom_nav_back_long"`, `"custom_nav_home_long"`, and `"custom_nav_menu_long"`.
    - Retrieve system framework context from `param.getThisObject().mContext`.
    - Call `GlobalActionBridge.handleAction(context, prefKey)` to execute the assigned MultiActionSettings option.
    - Set result to `Boolean.TRUE` to consume the event and strictly override default MIUI/HyperOS settings.
- UI & Preferences:
  * Replaced 4 obsolete seekbars under `prefs_key_system_ui_navigation_custom` in `system_ui_navigation.xml` with:
    - Back Long Press: `prefs_key_navigation_bar_back_long_press` -> `MultiActionSettings`
    - Home Long Press: `prefs_key_navigation_bar_home_long_press` -> `MultiActionSettings`
    - Menu Long Press: `prefs_key_navigation_bar_menu_long_press` -> `MultiActionSettings`
- Target Files:
  * `library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress.java`
  * `library/core/src/main/res/xml/system_ui_navigation.xml`
  * `library/core/src/main/res/values/strings_app.xml`
  * Registered in: `SystemFrameworkV.java` & `SystemFrameworkB.java`
