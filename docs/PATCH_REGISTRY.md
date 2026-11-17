# AGENT INTERNAL PATCH REGISTRY & SMALI-TO-SOURCE REVERSE COMPILER DIRECTIVES
# Target Project: HyperCeiler-HOS2
# Agent Role: Autonomous Patch Translator & Upstream Synchronizer
# Timestamp Standard: Device Local Time (EET / Asia-Cairo)

# REGISTRY STATUS CONVENTION:
# - [STATUS: MERGED_TO_FORK]: Successfully verified locally and committed/pushed to fork repository main branch.
# - [STATUS: PENDING_MERGE]: Active in local smali bytecode / tested on device, NOT yet merged or pushed to fork.
# - [STATUS: REVERTED]: Patch was cancelled or undone per user instruction.

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

--- [PATCH: CP-01] [STATUS: MERGED_TO_FORK] BYPASS DOWNGRADE ---
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

--- [PATCH: CP-02] [STATUS: MERGED_TO_FORK] BYPASS VERIFICATION & DIGEST ---
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

--- [PATCH: CP-03] [STATUS: MERGED_TO_FORK] BYPASS RESOURCES.ARSC 4-BYTE ALIGNMENT ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/res/AssetManager;->nativeLoadResourceBag / loadApkAssets
  * Landroid/content/res/ApkAssets;->loadFromPath
- Logic Inversion:
  * Force bypass 4-byte alignment restriction for uncompressed resources.arsc on Android 11+.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/AssetManagerHook.kt
- Pref Key: prefs_key_system_framework_bypass_resource_arsc

--- [PATCH: CP-04] [STATUS: MERGED_TO_FORK] BYPASS DIGEST & SIGNATURE LINEAGE ---
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

--- [PATCH: CP-05] [STATUS: MERGED_TO_FORK] BYPASS EXACT SIGNATURE MATCH ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/pm/PackageParser$SigningDetails;->signaturesMatchExactly
  * Landroid/content/pm/SigningDetails;->signaturesMatchExactly
- Logic Inversion:
  * Intercept signaturesMatchExactly and return true unconditionally. Allows split APKs with different certs.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/SigningDetailsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_exact_signature_check

--- [PATCH: CP-06] [STATUS: MERGED_TO_FORK] USE PREVIOUS SIGNATURES (DANGEROUS) ---
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

--- [PATCH: CP-07] [STATUS: MERGED_TO_FORK] ALLOW HIDDEN APIS FOR SYSTEM APPS ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Landroid/content/pm/ApplicationInfo;->isAllowedToUseHiddenApis
- Logic Inversion:
  * Return true if app is system or matches target criteria, granting unrestricted hidden API access.
- Kotlin Implementation:
  * Target File: library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/corepatch/ApplicationInfoHook.kt
- Pref Key: prefs_key_system_framework_allow_hidden_apis

--- [PATCH: CP-08] [STATUS: MERGED_TO_FORK] BYPASS SHARED USER SIGNATURE MATCH ---
[TIMESTAMP]: 2026-11-16 05:05:00 EET
- Smali Target:
  * Lcom/android/server/pm/SharedUserSetting;->signaturesMatch
  * Lcom/android/server/pm/ReconcilePackageUtils;->reconcilePackages
- Logic Inversion:
  * Allow packages sharing the same sharedUserId to install even if signing certs differ.
- Kotlin Implementation:
  * Target Files: SharedUserSettingHook.kt, ReconcilePackageUtilsHook.kt
- Pref Key: prefs_key_system_framework_core_patch_shared_user

--- [PATCH: CP-09] [STATUS: MERGED_TO_FORK] DISABLE PACKAGE VERIFICATION AGENT ---
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

--- [PATCH: CP-10] [STATUS: MERGED_TO_FORK] BYPASS OEM INSTALLATION BLOCKLIST ---
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

--- [PATCH: NB-01] [STATUS: MERGED_TO_FORK] 3-BUTTON LONG PRESS ACTION INTERCEPTION & SYSTEM OVERRIDE ---
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

--- [PATCH: NB-02] [STATUS: MERGED_TO_FORK] FORCE HOME KEY LONG PRESS SUPPORT (NOVA LAUNCHER COMPATIBILITY) ---
[TIMESTAMP]: 2026-11-16 11:35:00 EET
- Smali Target:
  * Lcom/android/server/policy/MiuiSingleKeyRule;->supportLongPress()Z
  * Helper class: `NavigationButtonLongPress$3.smali`
- Problem & Discovery:
  * When using third-party launchers (e.g. Nova Launcher) or when Circle to Search is bound to Home in OEM settings without POCO Launcher set as default, Xiaomi's `MiuiSingleKeyRule.supportLongPress()` returns `false` for KEYCODE_HOME (3).
  * This prevented the system from ever waiting for the long press timer or calling `getFunction("long_press_home_key")`.
- Logic & Fix:
  * Hook `MiuiSingleKeyRule.supportLongPress()`.
  * Inspect `mKeyCode` via `getIntField(this, "mKeyCode")`.
  * If `mKeyCode == 3` (KeyEvent.KEYCODE_HOME):
    - Call `param.setResult(Boolean.TRUE)` directly and unconditionally (identical to MiCTS architecture).
  * Forces the Android System Server framework to always listen to and activate Home key long press timer regardless of launcher default status.
- Source Update Status:
  * Bytecode applied, zipalign-verified, and signed with `CN=MIDO` keystore in `/storage/emulated/0/Download/HyperCeiler-2.10.166-20261116-release_signed.apk`.
  * Successfully installed on device over previous package via root.

================================================================================
MODULE 2: Home Long Press & POCO Launcher UI Visibility Fix
Source Files: 
  - smali/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress$1.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress$2.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress$3.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress.smali
  - smali/ts.smali (PackagesUtils / HeaderManager)
Target Source Package:
  - com.sevtinge.hyperceiler.libhook.rules.systemframework.navbar.NavigationButtonLongPress.java
  - com.sevtinge.hyperceiler.utils.PackagesUtils.java
Timestamp Registered: 2026-11-16 12:08:00 EET
================================================================================

--- [PATCH: NAV-01] [STATUS: MERGED_TO_FORK] HOME LONG PRESS ACTION REDIRECTION & TRIGGER ---
- Root Cause: HyperOS routes Home key through HomeKeyRule with action "long_press_home_key_no_ui". Original hook only matched "long_press_home_key". Furthermore, supportAOSPTriggerFunction(3) in MiuiShortcutTriggerHelper checked assist_long_press_home_enabled setting which is null or disabled.
- Fix:
  1. NavigationButtonLongPress$1: matches action.contains("home"), returns "custom_nav_home_long".
  2. NavigationButtonLongPress$2: matches action.contains("home"), executes GlobalActionBridge.handleAction(context, "prefs_key_navigation_bar_home_long_press").
  3. NavigationButtonLongPress & NavigationButtonLongPress$3: hooks MiuiShortcutTriggerHelper.supportAOSPTriggerFunction(int), forcing return TRUE when keyCode == 3.

--- [PATCH: UI-01] [STATUS: REVERTED] POCO LAUNCHER (com.mi.android.globallauncher) VISIBILITY FIX ---
- Status: REVERTED per user request (2026-11-16 12:53:00 EET).
- Original Cause: HeaderManager.isHeaderAvailable called PackagesUtils.checkAppStatus("com.miui.home"). While isUninstall had a fallback for "com.mi.android.globallauncher", isHidden threw NameNotFoundException on "com.miui.home" and returned true, marking POCO Launcher as hidden and removing it from all UI lists.
- Revert Action:
  - Removed `move-object p1, v4` from `ts.smali`, restoring original isHeaderAvailable package resolution behavior exactly as it was.

================================================================================
MODULE 3: Circle to Search (CTS) Trigger & UI Renaming (Offline / Non-Stock Launcher)
Source Files:
  - smali/com/sevtinge/hyperceiler/sub/MultiActionSettings.smali
  - smali/rr.smali (GlobalActionBridge)
Target Source Package:
  - com.sevtinge.hyperceiler.sub.MultiActionSettings.java
  - com.sevtinge.hyperceiler.libhook.appbase.systemframework.GlobalActionBridge.java
Timestamp Registered: 2026-11-16 12:45:00 EET
================================================================================

--- [PATCH: CTS-01] [STATUS: MERGED_TO_FORK] CIRCLE TO SEARCH NATIVE INTEGRATION & UI VISIBILITY ---
[TIMESTAMP]: 2026-11-16 12:45:00 EET
- Root Cause:
  1. In HyperOS, triggering Google Voice Assistant or OEM search delegates to `IOverviewProxy`, which fails silently when Nova Launcher (or any non-stock launcher) is active.
  2. HyperCeiler previously only allowed "Circle to Search" to show up under specific gestures (`supportsAssistantGestureAction()`).
  3. Action ID 20 (`prefs_key_google_voice_assistant`) dispatched to `SystemUiProxyWrapper` which is disconnected when stock launcher isn't default.
  4. The Voice Interaction Manager Service (`VIMS`) hooker expects `bundle.putBoolean("micts_trigger", true)` to hook `Resources.getString` for Contextual Search.
- Fix & Bytecode Implementation:
  1. In `MultiActionSettings.smali`:
     - Modified `supportsAssistantGestureAction()` to return `true` unconditionally, allowing Circle to Search (`prefs_key_google_circle_to_search`) to be selected for Navigation Bar actions.
     - In `generateScreenColorPreference()`, added key check for `prefs_key_google_voice_assistant` to display its title as "Circle to Search" in the UI.
  2. In `rr.smali` (`GlobalActionBridge`):
     - Action ID 20 (`prefs_key_google_voice_assistant`) was redirected from the broken `SystemUiProxyWrapper` directly into the Voice Interaction CTS pipeline (jumped to `:pswitch_4`).
     - In the CTS voice interaction Bundle preparation, added `bundle.putBoolean("micts_trigger", true)`. This activates the VIMS contextual search hook in LSPosed, enabling Google Circle to Search to pop up instantly and reliably even when Nova Launcher is the default launcher.
- Status:
  - Bytecode assembled via `apktool b`.
  - Aligned via `zipalign -p 4`.
  - Signed via `apksigner` with `permanent_release.keystore` (CN=MIDO, alias `mido`).
  - Saved to `/storage/emulated/0/Download/HyperCeiler-2.10.166-20261116-release_signed.apk`.
  - Installed successfully on device via root/pm.

================================================================================
MODULE 4 [TAG: Suki Splash]: SukiSU Nova (com.sukisu.nova) Splash Screen Removal
Source Origin: com.sukisu.nova (v3.0.0 / classes.dex)
Target App / Package: com.sukisu.nova
Target Activity: com.sukisu.nova.ui.mqptwwwhxutActivity (Launcher Target)
Target Smali Files:
  - apk_smali/smali/wv6.smali (Root Compose Content Layout & Splash Trigger)
  - apk_smali/smali/pk6.smali (In-App Splash Composable Renderer: method `d`)
Timestamp Registered: 2026-11-16 13:16:33 EET
================================================================================

--- [PATCH: SUKI-01] [TAG: Suki Splash] BYPASS IN-APP WHITE SPLASH SCREEN ---
[TIMESTAMP]: 2026-11-16 13:16:33 EET
- Root Cause:
  1. `mqptwwwhxutActivity` loads root Compose tree via `wc5` -> `wv6`.
  2. Inside `wv6`, a `MutableState<Boolean>` (`dl3Var5`) tracks `splashFinished`, initialized to `Boolean.FALSE` (`sget-object v7, Ljava/lang/Boolean;->FALSE`).
  3. When `dl3Var5.getValue()` is false, `wv6` calls `Lpk6;->d(ZJLyz1;Lmv0;I)V`, which renders a hardcoded white background Compose overlay containing the anime avatar (`b41.r`), "SukiSU Nova", "Kernel-based Root Solution", and quote texts, completely ignoring the system dark mode.
- Bytecode Modification:
  1. In `wv6.smali`:
     - Line ~794: In the remember factory block of `dl3Var5`, change:
       ```smali
       sget-object v7, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
       ```
       to:
       ```smali
       sget-object v7, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
       ```
     - Line ~1618: At the splash display gate:
       ```smali
       invoke-virtual {v3}, Ljava/lang/Boolean;->booleanValue()Z
       move-result v3
       if-nez v3, :cond_18
       ```
       Change `if-nez v3, :cond_18` to:
       ```smali
       goto :cond_18
       ```
       This forces Compose to completely bypass calling `Lpk6;->d` and follow the `splashFinished` block (`0x53672e68`), ensuring smooth composition directly to the main screen.
- Packaging & Status:
  - Assembled via `apktool b`.
  - Aligned via `zipalign -p -f 4`.
  - Signed via `apksigner` with `permanent_release.keystore` (Alias: `mido`, pass: `000000`, v2+v3 scheme).
  - Saved to `/storage/emulated/0/Download/SukiSU_Nova_NoSplash.apk`.
  - Installed via root: `cat <apk> | su -c "pm install -r -S <size>"`.
  - Tested and verified: Splash completely gone, dark mode opens instantly.

================================================================================
MODULE 5: Third-Party Launcher Full Screen Gestures Unlock (POCO Launcher / MIUI Home)
Source Origin: com.mi.android.globallauncher / com.miui.home
Target Source Package: com.sevtinge.hyperceiler.libhook.rules.home.navigation.ThirdPartyLauncherGestureFix
Target Smali Files:
  - smali/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix$TrueReplacer.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix$BaseRecentsImplHooker.smali
  - smali/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix$OverviewComponentHooker.smali
  - smali/com/sevtinge/hyperceiler/libhook/app/Home/os2/HomePhoneOld.smali
  - smali/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.smali
Timestamp Registered: 2026-11-16 22:23:00 EET
================================================================================

--- [PATCH: GESTURE-01] [STATUS: MERGED_TO_FORK] THIRD-PARTY LAUNCHER FULL SCREEN GESTURES UNLOCK ---
[TIMESTAMP]: 2026-11-17 05:28:00 EET
- Root Cause:
  1. UI Warning Lock: When non-stock launcher (e.g. Nova Launcher) is default, opening "System navigation" and tapping "Gestures" calls `BuildConfigUtils.isUsePocoHomeAsCurrentDefaultHome()` / `isUseMiuiHomeAsDefaultHome()`. If neither is default home, it triggers an AlertDialog blocking gesture selection.
  2. Runtime Touch & Stub Dismantle: If `force_fsg_nav_bar` is enabled, `OverviewComponentObserver.updateOverviewTargets()` detects `mIsHomeAndOverviewSame == false` and calls `BaseRecentsImpl.setIsUseMiuiHomeAsDefaultHome(false)`. This in turn causes `updateFsgWindowState()` to invoke `removeNavStubView()` and `clearBackStubWindow()`, effectively killing edge swipes (Back) and bottom swipes (Home / Recents) after the first event.
- Fix & Source Code Implementation:
  1. Created `ThirdPartyLauncherGestureFix.java`:
     - Intercepts `BuildConfigUtils` methods (`isUsePocoHomeAsCurrentDefaultHome`, `isUsePocoHomeAsDefaultHome(Context)`, `isUseMiuiHomeAsDefaultHome(Context)`) to return `Boolean.TRUE` unconditionally.
     - Hooks `BaseRecentsImpl.setIsUseMiuiHomeAsDefaultHome(boolean)` to always replace argument 0 with `Boolean.TRUE`.
     - Hooks `OverviewComponentObserver.updateOverviewTargets()` (after): sets `mIsHomeAndOverviewSame = true` and reinforces `BaseRecentsImpl.setIsUseMiuiHomeAsDefaultHome(true)` to keep gesture windows and touch stubs permanently alive.
  2. Registered hook in:
     - `HomePhoneOld.java` (OS 2)
     - `HomePhone.java` (OS 3)
  3. In `XposedInitEntry.java`:
     - Added package alias mapping for `com.mi.android.globallauncher` -> `com.miui.home` to ensure exact match detection during module bootstrap.

================================================================================
MODULE 6: POCO Launcher UI Visibility & Header Support
Source Origin: com.mi.android.globallauncher / com.miui.home
Target Source Package:
  - com.sevtinge.hyperceiler.utils.PackagesUtils.java
  - com.sevtinge.hyperceiler.utils.ScopeManager.kt
  - com.sevtinge.hyperceiler.home.utils.HeaderManager.java
Timestamp Registered: 2026-11-17 05:28:00 EET
================================================================================

--- [PATCH: UI-02] [STATUS: MERGED_TO_FORK] POCO LAUNCHER HYPERCEILER HOME LIST VISIBILITY ---
[TIMESTAMP]: 2026-11-17 05:28:00 EET
- Root Cause:
  1. HyperCeiler defines its Home header with summary `com.miui.home`.
  2. On POCO devices where `com.mi.android.globallauncher` is installed instead of `com.miui.home`, `PackagesUtils.isHidden(context, "com.miui.home")` threw `NameNotFoundException` and returned `true`, causing `HeaderManager.isHeaderAvailable` to evaluate to `false` and hiding the Home category from the main dashboard.
  3. `ScopeManager.containsScopePackage` did not treat `com.mi.android.globallauncher` and `com.miui.home` as interchangeable aliases.
- Fix & Implementation:
  1. In `PackagesUtils.java`: Added fallback to `com.mi.android.globallauncher` inside `isDisable()` and `isHidden()`.
  2. In `ScopeManager.kt`: Updated `containsScopePackage` to match `com.miui.home` <-> `com.mi.android.globallauncher` interchangeably.
  3. In `HeaderManager.java`: Enhanced `getCurrentScopeManagedPackages` to recognize active scope for `com.mi.android.globallauncher` mapped to `com.miui.home`.


