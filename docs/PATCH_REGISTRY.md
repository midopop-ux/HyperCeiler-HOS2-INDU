# MASTER PATCH REGISTRY & AUDIT ARCHITECTURE
# Target Project: HyperCeiler-HOS2 & Native System Extensions
# Primary Standard: Reverse-Engineered Smali Bytecode <---> High-Level Git Source Sync
# Timestamp Standard: Device Local Time (EET / UTC+2)

================================================================================
SECTION 0: REGISTRY DIRECTIVES & OPERATIONAL CONTRACT
================================================================================
1. Purpose & Scope:
   - This document serves as the absolute single source of truth for all bytecode patches, smali injections, and source implementations across HyperCeiler and hooked system components.
   - It tracks exact root causes, bytecode offsets, target source mappings, verification artifacts, and merge states.

2. Structural Hierarchy:
   - [Pinned Section 1]: SukiSU Nova Splash Screen Removal (Permanent Top Anchor).
   - [Descending History]: All remaining historical modules and patches sorted chronologically in descending order (Newest First), allowing instant retrieval of recent changes without scanning the entire document.

3. Status Convention:
   - [STATUS: MERGED_TO_FORK]: Verified locally and cleanly integrated/pushed to upstream GitHub fork branch.
   - [STATUS: PENDING_MERGE]: Active in local smali bytecode or under local verification; not yet synced to remote fork.
   - [STATUS: REVERTED]: Patch was cancelled or undone per user instruction.

4. Universal Offline & Safety Directives:
   - Offline Smali Lock: When working locally/offline, never trigger GitHub Actions API dispatches, never push to remote repositories, and keep all operations strictly local.
   - Filesystem Safety Lock: Strictly prohibit destructive operations (`rm`, `mv`) outside the local scratch/workspace directory.

5. Strict Anti-Obfuscation Directive (Compulsory Structural Heuristics):
   - [FATAL PROHIBITION]: Strictly prohibited from documenting or indexing patches using obfuscated short names (e.g. `a.smali`, `wv6.smali`, `mqptwwwhxutActivity`), as R8/ProGuard reshuffles all identifiers across builds.
   - [MANDATORY STANDARD]: Every smali/bytecode patch MUST be anchored using permanent invariants:
     1. Unobfuscated string constants (UI texts, preference keys, system action strings).
     2. Android Framework / Jetpack API signatures (`setContent`, `Activity` lifecycle, `mutableStateOf`).
     3. Opcode execution patterns and bytecode structural logic.

================================================================================
SECTION 1 [PINNED TOP ANCHOR]: SukiSU Nova Splash Screen Removal (Universal Heuristic)
Target App / Package: com.sukisu.nova
Primary Heuristic Anchor: String literal "Kernel-based Root Solution" (Persistent across all builds & R8 obfuscation)
Timestamp Registered: 2026-11-16 (Initial) | 2026-11-17 (Updated with Universal Heuristics)
================================================================================

--- [PATCH: SUKI-01] [TAG: Suki Splash] BYPASS IN-APP WHITE SPLASH SCREEN ---
[CRITICAL LESSON LEARNED]:
  * R8 obfuscation generates random class names (e.g. `mqptwwwhxutActivity`, `wv6`, `pk6` in Build X vs `gzdzdezpwsmActivity`, `vv6`, `bu0` in Build 35004).
  * NEVER search by obfuscated class names. ALWAYS use the structural heuristic anchors below.

[UNIVERSAL HEURISTIC IDENTIFICATION (FAST 2-STEP LOOKUP)]:
  1. Root Activity Detection:
     - Manifest Query: `aapt2 dump xmltree --file AndroidManifest.xml <apk> | grep -B 2 -A 5 "theme_trial"`
     - Or search `.smali` files for string `"theme_trial"`. The Activity holding this is the Compose Launcher.
  2. Root Compose & Splash Detection:
     - Search for string `"Kernel-based Root Solution"` across all `.smali` files.
       Command: `grep -rn "Kernel-based Root Solution" smali*/`
     - That `.smali` file contains the Splash Composable rendering block.
     - Look for the parent Compose tree layout calling it (the `setContent` delegate).
     - In that parent file, locate:
       a) The remember factory initializing the splash boolean state to `Boolean.FALSE`:
          ```smali
          sget-object v7, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
          invoke-static {v7}, Ll43;->M(Ljava/lang/Object;)Lwz3;
          ```
       b) The splash visibility gate:
          ```smali
          invoke-virtual {v3}, Ljava/lang/Boolean;->booleanValue()Z
          move-result v3
          if-nez v3, :cond_xx
          ```

[BYTECODE MODIFICATIONS (TWO-POINT BYPASS)]:
  1. Point A (Initial State Bypass):
     - Change:
       ```smali
       sget-object v7, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
       ```
       To:
       ```smali
       sget-object v7, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
       ```
  2. Point B (Composable Gate Bypass):
     - Change:
       ```smali
       if-nez v3, :cond_xx
       ```
       To:
       ```smali
       goto :cond_xx
       ```
     - Effect: Forces the Compose runtime to skip rendering the white splash overlay and transition directly to the main dashboard in system dark mode.

[BUILD-SPECIFIC MAPPINGS FOR REFERENCE]:
  * Build 35004 (v3.0.0):
    - Activity: `com/sukisu/nova/ui/gzdzdezpwsmActivity.smali`
    - Parent Compose Layout: `vv6.smali` (Line 794 & Line 1618)
    - Splash Text Holder: `bu0.smali` (Line 4135)
  * Historical Build (~35000):
    - Activity: `mqptwwwhxutActivity.smali`
    - Parent Compose Layout: `wv6.smali` (Line 794 & Line 1618)
    - Splash Renderer: `pk6.smali` (method `d`)

[PACKAGING & SIGNING PIPELINE]:
  1. `apktool b <dir> -o unsigned.apk`
  2. `zipalign -p -f 4 unsigned.apk aligned.apk`
  3. `apksigner sign --ks /data/user/0/com.agy/files/.gemini/keystore/permanent_release.keystore --ks-key-alias mido --ks-pass pass:000000 --out <dest_apk> aligned.apk`
  4. Verified with: `apksigner verify --verbose <dest_apk>` (v2 & v3 active)

================================================================================
SECTION 2: DESCENDING AUDIT HISTORY (NEWEST FIRST)
================================================================================

================================================================================
MODULE 10: Navigation Button Long-Press Priority & POCO Launcher UI Consolidation
Source Origin: com.android.server.policy / com.sevtinge.hyperceiler
Target Source Package:
  - app/src/main/res/xml/settings_header.xml
  - library/core/src/main/res/values/strings.xml
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemframework/navbar/NavigationButtonLongPress.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SystemFramework/SystemFrameworkV.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SystemFramework/SystemFrameworkB.java
Timestamp Registered: 2026-11-17 21:00:00 EET
================================================================================

--- [PATCH: NAV-02] [STATUS: PENDING_MERGE] NAVIGATION BUTTON LONG-PRESS PRIORITY OVERRIDE & KEY MISMATCH FIX ---
[TIMESTAMP]: 2026-11-17 21:00:00 EET
- Root Cause:
  1. Key Mismatch in SystemFramework: `system_ui_navigation.xml` toggled `prefs_key_system_ui_navigation_custom`, but `NavigationButtonLongPress.java` and `SystemFramework*.java` checked `system_ui_navigation_custom` without prefix, causing the hook to evaluate false and never execute inside `system_server`.
  2. Loss of Priority: MIUI OEM defaults and `MiuiSingleKeyRule` took precedence because the hook was bypassed.
  3. CTS/3rd-Party Launcher Bypass: `MiuiSingleKeyRule.supportLongPress()` and `MiuiShortcutTriggerHelper.supportAOSPTriggerFunction(3)` were placed inside the custom check, meaning if custom navigation was disabled, non-stock launchers (Nova Launcher) lost Circle to Search and Home key long-press support.
- Fix & Source Code Implementation:
  1. `NavigationButtonLongPress.java`:
     - Made `supportAOSPTriggerFunction(3)` and `supportLongPress()` (mKeyCode == 3) unconditional, guaranteeing Circle to Search and Home key responsiveness with 3rd-party launchers.
     - Dual-check preference keys: checks both `prefs_key_system_ui_navigation_custom` and `system_ui_navigation_custom`.
     - `MiuiSingleKeyRule.getFunction()` intercepts back, home, and menu actions (`action.contains(...)`) and assigns custom actions.
     - `ShortCutActionsUtils.triggerFunction()` routes execution to `GlobalActionBridge.handleAction()` and returns `Boolean.TRUE` to consume event.
  2. `SystemFrameworkV.java` & `SystemFrameworkB.java`:
     - Initialized `NavigationButtonLongPress` with `true` to ensure framework-level responsiveness is always active.
  3. UI Header Consolidation (`settings_header.xml` & `strings.xml`):
     - Removed `com.miui.home` header. Replaced with `poco_home` targeting `com.mi.android.globallauncher` titled "POCO Launcher" (`@string/mihome`) and pointing to `HomeFragment`.
     - Preserved full launcher settings including 3rd-party gesture toggle and long-press actions under POCO Launcher.

================================================================================
MODULE 9: Dedicated POCO Launcher Tab & Independent Header Architecture
Source Origin: com.mi.android.globallauncher
Target Source Package:
  - app/src/main/res/xml/settings_header.xml
  - library/core/src/main/res/xml/poco_launcher.xml
  - library/core/src/main/java/com/sevtinge/hyperceiler/hooker/PocoLauncherFragment.java
  - app/src/main/java/com/sevtinge/hyperceiler/home/utils/HeaderManager.java
Timestamp Registered: 2026-11-17 07:45:00 EET
================================================================================

--- [PATCH: UI-04] [STATUS: MERGED_TO_FORK (bf0a7d1ec)] DEDICATED POCO LAUNCHER TAB & CLEAN SEPARATION ---
[TIMESTAMP]: 2026-11-17 07:45:00 EET
- Architecture & Source Implementation:
  1. Created dedicated `poco_launcher.xml` with target `app:quick_restart="com.mi.android.globallauncher"` and third-party gesture toggle.
  2. Created dedicated `PocoLauncherFragment.java` bound to `R.xml.poco_launcher`.
  3. Added standalone header entry `@+id/poco_home` in `settings_header.xml` with package `com.mi.android.globallauncher` and title "POCO Launcher".
  4. Removed old in-place hijacking of `com.miui.home` header in `HeaderManager.java`, granting POCO Launcher complete independence from Chinese MIUI Home settings.
  5. Built and verified online on GitHub Actions (Run ID: 44026999415, Artifact ID: 14304245095).

================================================================================
MODULE 7: POCO Launcher Quick Restart Target & AppLabel Null Fix
Source Origin: com.mi.android.globallauncher / com.miui.home
Target Source Package:
  - library/core/src/main/java/com/sevtinge/hyperceiler/utils/PackagesUtils.java
  - library/core/src/main/java/com/sevtinge/hyperceiler/dashboard/DashboardFragment.java
Timestamp Registered: 2026-11-17 07:25:00 EET
================================================================================

--- [PATCH: UI-03] [STATUS: MERGED_TO_FORK (bf0a7d1ec)] REBOOT NULL DIALOG FIX & POCO RESTART TARGET ---
[TIMESTAMP]: 2026-11-17 07:25:00 EET
- Root Cause:
  1. On POCO devices where com.mi.android.globallauncher is installed, PackagesUtils.getAppLabel(context, "com.miui.home") threw NameNotFoundException and returned null.
  2. In DashboardFragment, clicking the quick restart icon on the top-right of System launcher passed "com.miui.home" to DialogHelper.showRestartDialog, resulting in the dialog: "Reboot null: Are you sure you want to restart now null ?".
- Fix & Source Implementation:
  1. PackagesUtils.java: Added fallback inside getAppLabel for "com.miui.home" to query "com.mi.android.globallauncher", resolving "POCO Launcher" app name.
  2. DashboardFragment.java: When restarting "com.miui.home", automatically routes target package to "com.mi.android.globallauncher" if installed.

================================================================================
MODULE 8: Third-Party Launcher Gestures & Circle to Search Hook Integration
Source Origin: com.mi.android.globallauncher / com.miui.home
Target Source Package:
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/home/gesture/GestureLine.kt
  - library/core/src/main/res/xml/home_gesture.xml
Timestamp Registered: 2026-11-17 07:25:00 EET
================================================================================

--- [PATCH: GESTURE-02] [STATUS: MERGED_TO_FORK (bf0a7d1ec)] FULL SCREEN GESTURES HOOK (0MS HOME, RECENTS HOLD, CTS LONG PRESS) ---
[TIMESTAMP]: 2026-11-17 07:25:00 EET
- Features Implemented via Xposed Hooks:
  1. Instant Home (0ms): Hooked NavStubView.performAppToHome to immediately inject KEYCODE_HOME (3) and finishRecentsActivityDirectly.
  2. Swipe and Hold for Recents (0.5s): Hooked NavStubView.isAllowToAppHoldState to return TRUE, and hooked enterAppHoldState to inject KEYCODE_APP_SWITCH (187).
  3. Gesture Line Long Press to Circle to Search: Hooked NavStubGestureEventManager.onLongPress and handleLongPressEvent to trigger GlobalActionBridge.handleAction("prefs_key_navigation_bar_home_long_press"), executing Circle to Search directly via HomeNativeGestureActions.launchGoogleCircleToSearchFromHome.
  4. HyperCeiler Settings Toggle: Added prefs_key_home_navigation_third_party_launcher in home_gesture.xml with string resources in Arabic and Chinese.


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

================================================================================
MODULE 5: Third-Party Launcher Full Screen Gestures Unlock (POCO Launcher / MIUI Home)
Source Origin: com.mi.android.globallauncher / com.miui.home
Target Source Package: com.sevtinge.hyperceiler.libhook.rules.home.navigation.ThirdPartyLauncherGestureFix
Target Smali / Source Files:
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/home/navigation/ThirdPartyLauncherGestureFix.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/Home/os2/HomePhoneOld.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/Home/os3/HomePhone.java
  - library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.java
Timestamp Registered: 2026-11-17 05:28:00 EET
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
MODULE 2.2: Home Long Press & POCO Launcher UI Visibility Fix
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
MODULE 2.1: Navigation Bar 3-Button Long Press Custom Actions
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

===============================================================================
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
