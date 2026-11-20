/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.securitycenter.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MenuItem;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Delegate uninstall actions in Security Center "App Management" to a specified uninstaller.
 * <p>
 * Hook timing is governed by setting {@code security_center_uninstall_hook_point} (see {@link #init()}):
 * <ol>
 *     <li>{@code menu}: Host "Uninstall" menu item callback
 *     (typically {@code com.miui.appmanager.fragment.ApplicationsDetailsFragment.optionsItemSelected(MenuItem)});</li>
 *     <li>{@code core}: Low-level uninstall calls ({@link #CORE_APIS} + methods in {@code AppManageUtils} matching "uninstall"),
 *     covering entry points that do not go through the options menu callback;</li>
 *     <li>{@code both} (default): Intercept both entry points.</li>
 * </ol>
 * On interception:
 * <ol>
 *     <li>Blocks the original host uninstall invocation ({@code param.setResult(...)});</li>
 *     <li>Resolves target uninstaller according to preference {@code security_center_uninstaller}
 *     (empty = system default resolver, {@code __follow_default_installer__} = system default installer,
 *     or explicit package name locked via {@code setComponent});</li>
 *     <li>Launches reconstructed {@link Intent#ACTION_DELETE} + {@code package:<pkg>}:
 *     uses foreground Activity if present (same task stack and transition animation),
 *     otherwise falls back to host Application context with {@code FLAG_ACTIVITY_NEW_TASK}.</li>
 * </ol>
 * <p>
 * Does not rewrite {@code Activity#startActivity} to avoid clearing target package selections.
 */
@SuppressLint("DiscouragedApi")
public class UninstallWithDefaultPackageManager extends BaseHook {

    private static final String SCHEME_PACKAGE = "package";

    /**
     * Extra key carrying the target package name in app details pages.
     */
    private static final String EXTRA_PACKAGE_NAME = "package_name";

    /**
     * APK MIME type used to probe the system default installer.
     */
    private static final String MIME_APK = "application/vnd.android.package-archive";

    /**
     * String resource identifiers for the host "Uninstall" menu item title matching.
     */
    private static final String[] UNINSTALL_STRING_NAMES = {
        "app_manager_uninstall_text",
        "app_manager_uninstall",
        "uninstall"
    };

    /**
     * Host "Uninstall" menu item ID (fallback when title matching fails).
     */
    private static final int UNINSTALL_ITEM_ID = 2;

    /**
     * Preference key for uninstaller app selection.
     */
    private static final String PREFS_UNINSTALLER = "security_center_uninstaller";

    /**
     * Token representing following the system default installer.
     */
    private static final String FOLLOW_DEFAULT_INSTALLER = "__follow_default_installer__";

    /**
     * Preference key for hook point selection.
     */
    private static final String PREFS_HOOK_POINT = "security_center_uninstall_hook_point";

    private static final String HOOK_POINT_MENU = "menu";
    private static final String HOOK_POINT_CORE = "core";
    private static final String HOOK_POINT_BOTH = "both";

    /**
     * Low-level package uninstall APIs hooked in "core" mode.
     */
    private static final String[][] CORE_APIS = {
        {"android.content.pm.PackageInstaller", "uninstall"},
        {"android.app.ApplicationPackageManager", "deletePackage"},
        {"android.app.ApplicationPackageManager", "deletePackageAsUser"},
        {"android.app.ApplicationPackageManager", "deletePackageVersioned"},
        {"android.content.pm.IPackageManager$Stub$Proxy", "deletePackage"},
        {"android.content.pm.IPackageManager$Stub$Proxy", "deletePackageAsUser"},
        {"android.content.pm.IPackageManager$Stub$Proxy", "deletePackageVersioned"},
    };

    /**
     * Regex pattern to match package names in method arguments.
     */
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");

    private Method mMenuClickMethod;

    private String mUninstallerSetting = "";

    private String mHookPoint = HOOK_POINT_BOTH;

    /**
     * Most recently resumed Activity, used to launch uninstall intent in core mode.
     */
    private volatile WeakReference<Activity> mLastActivity = new WeakReference<>(null);

    // ==================== Entry Point 1: Host "Uninstall" Menu Item ====================

    private final IMethodHook mMenuItemHook = new IMethodHook() {
        @Override
        public void before(HookParam param) {
            try {
                Object[] args = param.getArgs();
                if (args == null || args.length == 0 || !(args[0] instanceof MenuItem)) {
                    return;
                }

                Activity activity = resolveActivity(param.getThisObject());
                if (activity == null) {
                    XposedLog.w(TAG, getPackageName(), "[menu] cannot resolve Activity, skip");
                    return;
                }

                if (!isUninstallMenuItem(activity, (MenuItem) args[0])) {
                    return;
                }

                String pkgName = resolvePackageName(param.getThisObject(), activity);
                if (TextUtils.isEmpty(pkgName)) {
                    XposedLog.w(TAG, getPackageName(),
                        "[menu] uninstall menu item clicked but package name not found");
                    return;
                }

                XposedLog.d(TAG, getPackageName(), "[menu] uninstall menu item clicked: pkg=" + pkgName);
                if (forwardUninstall("menu", activity, pkgName)) {
                    // Block host silent uninstallation since the uninstaller UI is launched.
                    param.setResult(true);
                } else {
                    XposedLog.w(TAG, getPackageName(), "[menu] forward failed, let MIUI handle the menu item");
                }
            } catch (Throwable t) {
                XposedLog.w(TAG, getPackageName(), "[menu] failed to intercept uninstall menu item", t);
            }
        }
    };

    // ==================== Entry Point 2: Low-Level Uninstall Calls ====================

    /**
     * Records the foreground Activity in Security Center process for core mode launches.
     */
    private final IMethodHook mActivityResumeHook = new IMethodHook() {
        @Override
        public void before(HookParam param) {
            Object thisObject = param.getThisObject();
            if (thisObject instanceof Activity) {
                mLastActivity = new WeakReference<>((Activity) thisObject);
            }
        }
    };

    /**
     * Creates a hook callback for a specific low-level uninstall API.
     */
    private IMethodHook coreCallback(String apiName) {
        return new IMethodHook() {
            @Override
            public void before(HookParam param) {
                try {
                    String pkgName = extractPackageName(param.getArgs());
                    if (TextUtils.isEmpty(pkgName)) {
                        XposedLog.d(TAG, getPackageName(), "[core] " + apiName + " without package name, skip");
                        return;
                    }

                    XposedLog.d(TAG, getPackageName(), "[core] uninstall call via " + apiName + ": pkg=" + pkgName);
                    if (forwardUninstall("core", getLastActivity(), pkgName)) {
                        // Block host uninstallation.
                        param.setResult(null);
                    } else {
                        XposedLog.w(TAG, getPackageName(), "[core] forward failed, keep original uninstall path");
                    }
                } catch (Throwable t) {
                    XposedLog.w(TAG, getPackageName(), "[core] failed to intercept uninstall call via " + apiName, t);
                }
            }
        };
    }

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        // Locate the menu click callback using the MIME string anchor.
        // Optional member: failure drops menu interception without breaking core interception.
        mMenuClickMethod = optionalMember("UninstallMenuClick", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings(MIME_APK)))
                    .singleOrNull();
            }
        });
        return true;
    }

    @Override
    public void init() {
        mHookPoint = PrefsBridge.getString(PREFS_HOOK_POINT, HOOK_POINT_BOTH);
        mUninstallerSetting = PrefsBridge.getString(PREFS_UNINSTALLER, "");
        XposedLog.d(TAG, getPackageName(), "init: hookPoint=" + mHookPoint
            + ", uninstaller=" + describeUninstaller(mUninstallerSetting));

        boolean hookMenu = HOOK_POINT_MENU.equals(mHookPoint) || HOOK_POINT_BOTH.equals(mHookPoint);
        boolean hookCore = HOOK_POINT_CORE.equals(mHookPoint) || HOOK_POINT_BOTH.equals(mHookPoint);

        if (hookMenu) {
            if (mMenuClickMethod != null) {
                hookMethod(mMenuClickMethod, mMenuItemHook);
                XposedLog.d(TAG, getPackageName(), "[menu] hooked " + mMenuClickMethod);
            } else {
                XposedLog.w(TAG, getPackageName(),
                    "[menu] uninstall menu click not found, this entry is inactive");
            }
        }

        if (hookCore) {
            hookCoreUninstall();
            // Keep track of foreground Activity so core mode launches with proper transition and stack.
            hookAllMethods(Activity.class, "onResume", mActivityResumeHook);
        }
    }

    /**
     * Hooks low-level package uninstall methods.
     */
    private void hookCoreUninstall() {
        for (String[] api : CORE_APIS) {
            hookAllMethods(api[0], api[1], coreCallback(api[0] + "#" + api[1]));
        }

        Class<?> appManageUtils = findClassIfExists("com.miui.appmanager.AppManageUtils");
        if (appManageUtils == null) {
            return;
        }
        for (Method method : appManageUtils.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("uninstall")) {
                hookMethod(method, coreCallback(method.toString()));
            }
        }
    }

    // ==================== Forwarding ====================

    /**
     * Forwards the uninstall request to the chosen uninstaller.
     *
     * @param source Log marker ({@code menu} / {@code core})
     * @return true if successfully launched
     */
    private boolean forwardUninstall(String source, Activity activity, String pkgName) {
        Context context = activity != null ? activity : getHostContext();
        if (context == null) {
            XposedLog.w(TAG, getPackageName(), "[" + source + "] no Context available, cannot forward");
            return false;
        }

        XposedLog.d(TAG, getPackageName(), "[" + source + "] forward uninstall: pkg=" + pkgName
            + ", uninstaller=" + describeUninstaller(mUninstallerSetting));

        if (startUninstallIntent(source, context, activity == null,
            buildUninstallIntent(context, pkgName, mUninstallerSetting))) {
            return true;
        }

        if (!TextUtils.isEmpty(mUninstallerSetting)) {
            XposedLog.w(TAG, getPackageName(), "[" + source + "] uninstaller "
                + describeUninstaller(mUninstallerSetting) + " failed, fallback to system default resolver");
            return startUninstallIntent(source, context, activity == null,
                buildUninstallIntent(context, pkgName, ""));
        }
        return false;
    }

    private boolean startUninstallIntent(String source, Context context, boolean newTask, Intent intent) {
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
            XposedLog.d(TAG, getPackageName(), "[" + source + "] forwarded: " + intent);
            return true;
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[" + source + "] start uninstall Activity failed: " + intent, t);
            return false;
        }
    }

    /**
     * Builds the uninstall Intent with proper target package and ComponentName.
     */
    private Intent buildUninstallIntent(Context context, String pkgName, String uninstallerSetting) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.fromParts(SCHEME_PACKAGE, pkgName, null));

        String target = uninstallerSetting;
        if (FOLLOW_DEFAULT_INSTALLER.equals(uninstallerSetting)) {
            target = resolveDefaultInstallerPackage(context);
            if (TextUtils.isEmpty(target)) {
                XposedLog.w(TAG, getPackageName(),
                    "[target] default installer not found, fallback to system default resolver");
                return intent;
            }
        }

        if (TextUtils.isEmpty(target)) {
            return intent;
        }

        intent.setPackage(target);
        ComponentName component = resolveUninstallActivity(context, pkgName, target);
        if (component != null) {
            intent.setComponent(component);
        }
        return intent;
    }

    /**
     * Resolves the default package installer for APK MIME type.
     */
    private String resolveDefaultInstallerPackage(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent probe = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("file:///sdcard/__probe__.apk"), MIME_APK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo info = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null) {
                return info.activityInfo.packageName;
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[target] resolve default installer failed", t);
        }
        return null;
    }

    /**
     * Finds the Activity within target package that handles package:<pkg> deletion.
     */
    private ComponentName resolveUninstallActivity(Context context, String pkgName, String target) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent probe = new Intent(Intent.ACTION_DELETE, Uri.fromParts(SCHEME_PACKAGE, pkgName, null))
                .setPackage(target);
            List<ResolveInfo> infos = pm.queryIntentActivities(probe, 0);
            if (infos != null) {
                for (ResolveInfo info : infos) {
                    if (info.activityInfo == null || !target.equals(info.activityInfo.packageName)) {
                        continue;
                    }
                    return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                }
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[target] resolve uninstall activity failed", t);
        }
        return null;
    }

    /**
     * Retrieves host process Application context.
     */
    private Context getHostContext() {
        try {
            Class<?> activityThread = findClassIfExists("android.app.ActivityThread");
            if (activityThread == null) {
                return null;
            }
            Object application = callStaticMethod(activityThread, "currentApplication");
            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[core] getHostContext failed", t);
        }
        return null;
    }

    private Activity getLastActivity() {
        Activity activity = mLastActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    private static String describeUninstaller(String setting) {
        if (TextUtils.isEmpty(setting)) {
            return "system default";
        }
        if (FOLLOW_DEFAULT_INSTALLER.equals(setting)) {
            return "follow default installer";
        }
        return setting;
    }

    // ==================== Resolution Helpers ====================

    /**
     * Extracts package name from method arguments (String or VersionedPackage).
     */
    private String extractPackageName(Object[] args) {
        if (args == null) {
            return null;
        }

        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                if (PACKAGE_NAME_PATTERN.matcher(value).matches()) {
                    return value;
                }
            } else if (arg != null && "android.content.pm.VersionedPackage".equals(arg.getClass().getName())) {
                Object name = callMethod(arg, "getPackageName");
                if (name instanceof String && !TextUtils.isEmpty((String) name)) {
                    return (String) name;
                }
            }
        }
        return null;
    }

    /**
     * Determines whether the clicked MenuItem is the "Uninstall" item.
     */
    private boolean isUninstallMenuItem(Activity activity, MenuItem item) {
        CharSequence title = item.getTitle();
        if (!TextUtils.isEmpty(title)) {
            Resources hostRes = activity.getResources();
            String hostPkg = activity.getPackageName();
            for (String name : UNINSTALL_STRING_NAMES) {
                int resId = hostRes.getIdentifier(name, "string", hostPkg);
                if (resId == 0) {
                    continue;
                }
                if (title.equals(hostRes.getString(resId))) {
                    return true;
                }
            }
        }
        return item.getItemId() == UNINSTALL_ITEM_ID;
    }

    /**
     * Resolves hosting Activity from Activity or Fragment instance.
     */
    private Activity resolveActivity(Object thisObject) {
        if (thisObject instanceof Activity) {
            return (Activity) thisObject;
        }

        for (String methodName : new String[]{"getActivity", "requireActivity"}) {
            try {
                Object result = callMethod(thisObject, methodName);
                if (result instanceof Activity) {
                    return (Activity) result;
                }
            } catch (Throwable ignored) {
                // Not a fragment or method absent, try next resolution strategy.
            }
        }
        return null;
    }

    /**
     * Resolves package name from host PackageInfo field or Intent extra.
     */
    private String resolvePackageName(Object thisObject, Activity activity) {
        PackageInfo packageInfo = findPackageInfo(thisObject);
        if (packageInfo == null) {
            packageInfo = findPackageInfo(activity);
        }
        if (packageInfo != null && !TextUtils.isEmpty(packageInfo.packageName)) {
            return packageInfo.packageName;
        }

        Intent intent = activity.getIntent();
        if (intent != null) {
            String pkgName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            if (!TextUtils.isEmpty(pkgName)) {
                return pkgName;
            }
        }
        return null;
    }

    private PackageInfo findPackageInfo(Object target) {
        if (target == null) {
            return null;
        }

        for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = findFirstFieldByExactType(clazz, PackageInfo.class);
                if (field == null) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(target);
                if (value instanceof PackageInfo) {
                    return (PackageInfo) value;
                }
            } catch (Throwable ignored) {
                // Ignore reflection failures on single class, check superclass.
            }
        }
        return null;
    }
}
