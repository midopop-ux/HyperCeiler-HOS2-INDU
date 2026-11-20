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
package com.sevtinge.hyperceiler.libhook.rules.home.other;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * Delegate the "Uninstall" action from launcher icon long-press menu to a specified uninstaller.
 * <p>
 * Hooks a single entry point: the long-press shortcut menu "Uninstall" item
 * {@code SystemShortcutMenuItem$UninstallShortcutMenuItem#getOnClickListener()}.
 * When clicked, resolves the package name and forwards the intent directly,
 * bypassing the launcher built-in uninstall confirmation dialog.
 * <p>
 * Deliberately avoids intercepting {@code UninstallController#uninstallApp} / {@code #uninstallApps}:
 * at that point the launcher has already started the uninstallation animation and waits for
 * {@code DeleteObserver} callbacks; intercepting there would cause a false uninstallation visual
 * animation without actual uninstall. Taking over at the shortcut menu level cleanly covers the
 * user-visible uninstallation flow.
 * <p>
 * On click:
 * <ol>
 *     <li>Intercepts the original launcher uninstall flow with a custom listener;</li>
 *     <li>Resolves target uninstaller according to preference {@code home_other_uninstaller}
 *     (empty = system default resolver, {@code __follow_default_installer__} = system default installer,
 *     or explicit package name locked via {@code setComponent});</li>
 *     <li>Launches reconstructed {@link Intent#ACTION_DELETE} + {@code package:<pkg>} using
 *     the launcher Activity (same task stack).</li>
 * </ol>
 */
public class UninstallWithDefaultPackageManager extends BaseHook {

    private static final String SCHEME_PACKAGE = "package";

    /**
     * APK MIME type used to probe the system default installer.
     */
    private static final String MIME_APK = "application/vnd.android.package-archive";

    /**
     * "Uninstall" shortcut item in long-press menu.
     */
    private static final String UNINSTALL_MENU_ITEM =
        "com.miui.home.launcher.shortcuts.SystemShortcutMenuItem$UninstallShortcutMenuItem";

    /**
     * Launcher Application class, used to retrieve the active Launcher Activity.
     */
    private static final String LAUNCHER_APPLICATION = "com.miui.home.launcher.Application";

    /**
     * Preference key: uninstaller package or special token.
     */
    private static final String PREFS_UNINSTALLER = "home_other_uninstaller";

    /**
     * Token representing following the system default installer.
     */
    private static final String FOLLOW_DEFAULT_INSTALLER = "__follow_default_installer__";

    private String mUninstallerSetting = "";

    @Override
    public void init() {
        mUninstallerSetting = PrefsBridge.getString(PREFS_UNINSTALLER, "");
        XposedLog.d(TAG, getPackageName(), "init: uninstaller=" + describeUninstaller(mUninstallerSetting));

        hookUninstallMenuItem();
    }

    /**
     * Replaces the original OnClickListener with a custom listener.
     * The menu item is a reusable template across icons; target package is resolved on click.
     */
    private void hookUninstallMenuItem() {
        findAndChainMethod(UNINSTALL_MENU_ITEM, "getOnClickListener", new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                Object original = chain.proceed();
                return (View.OnClickListener) view -> {
                    try {
                        Activity launcher = getLauncherActivity();
                        String pkgName = resolveBindedPackageName(launcher);
                        if (TextUtils.isEmpty(pkgName)) {
                            XposedLog.w(TAG, getPackageName(),
                                "[menu] uninstall menu item clicked but package name not found");
                        } else {
                            XposedLog.d(TAG, getPackageName(), "[menu] uninstall menu item clicked: pkg=" + pkgName);
                            if (forwardUninstall(launcher != null ? launcher : view.getContext(), pkgName)) {
                                hideShortcutMenu(launcher);
                                return;
                            }
                            XposedLog.w(TAG, getPackageName(), "[menu] forward failed, let MIUI home handle the menu item");
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, getPackageName(), "[menu] failed to intercept uninstall menu item", t);
                    }

                    // Fallback to original launcher behavior if forwarding fails.
                    if (original instanceof View.OnClickListener) {
                        ((View.OnClickListener) original).onClick(view);
                    }
                };
            }
        });
    }

    // ==================== Forwarding ====================

    /**
     * Forwards the uninstall request to the chosen uninstaller.
     * Prefers Launcher Activity, falls back to host Application context.
     *
     * @return true if successfully launched
     */
    private boolean forwardUninstall(Context context, String pkgName) {
        boolean newTask = !(context instanceof Activity);
        Context host = context != null ? context : getHostContext();
        if (host == null) {
            XposedLog.w(TAG, getPackageName(), "[menu] no Context available, cannot forward");
            return false;
        }

        XposedLog.d(TAG, getPackageName(), "[menu] forward uninstall: pkg=" + pkgName
            + ", uninstaller=" + describeUninstaller(mUninstallerSetting));

        if (startUninstallIntent(host, newTask,
            buildUninstallIntent(host, pkgName, mUninstallerSetting))) {
            return true;
        }

        if (!TextUtils.isEmpty(mUninstallerSetting)) {
            XposedLog.w(TAG, getPackageName(), "[menu] uninstaller "
                + describeUninstaller(mUninstallerSetting) + " failed, fallback to system default resolver");
            return startUninstallIntent(host, newTask, buildUninstallIntent(host, pkgName, ""));
        }
        return false;
    }

    private boolean startUninstallIntent(Context context, boolean newTask, Intent intent) {
        if (newTask) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
            XposedLog.d(TAG, getPackageName(), "[menu] forwarded: " + intent);
            return true;
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[menu] start uninstall Activity failed: " + intent, t);
            return false;
        }
    }

    /**
     * Builds the uninstall Intent.
     * Empty uninstallerSetting -> system default resolver;
     * {@link #FOLLOW_DEFAULT_INSTALLER} -> system default installer;
     * otherwise explicit package name with resolved uninstall Activity.
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
     * Fallback to host process Application context.
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
            XposedLog.w(TAG, getPackageName(), "getHostContext failed", t);
        }
        return null;
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

    // ==================== Item Resolution ====================

    /**
     * Retrieves the current Launcher Activity instance.
     */
    private Activity getLauncherActivity() {
        try {
            Class<?> application = findClassIfExists(LAUNCHER_APPLICATION);
            if (application == null) {
                return null;
            }
            Object launcher = callStaticMethod(application, "getLauncher");
            if (launcher instanceof Activity) {
                return (Activity) launcher;
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "get launcher failed", t);
        }
        return null;
    }

    /**
     * Resolves package name bound to the long-pressed item.
     */
    private String resolveBindedPackageName(Activity launcher) {
        if (launcher == null) {
            return null;
        }
        try {
            Object layer = callMethod(launcher, "getShortcutMenuLayer");
            if (layer == null) {
                return null;
            }
            return resolvePackageName(callMethod(layer, "getBindedItemInfo"));
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[menu] resolve bound item info failed", t);
            return null;
        }
    }

    /**
     * Resolves package name from item info with cascading fallbacks.
     */
    private String resolvePackageName(Object itemInfo) {
        if (itemInfo == null) {
            return null;
        }

        Object pkgName = tryCallMethod(itemInfo, "getPackageName");
        if (pkgName instanceof String && !TextUtils.isEmpty((String) pkgName)) {
            return (String) pkgName;
        }

        Object component = tryCallMethod(itemInfo, "getTargetComponent");
        if (component instanceof ComponentName) {
            return ((ComponentName) component).getPackageName();
        }

        Object intent = tryCallMethod(itemInfo, "getIntent");
        if (intent instanceof Intent) {
            ComponentName intentComponent = ((Intent) intent).getComponent();
            if (intentComponent != null) {
                return intentComponent.getPackageName();
            }
        }
        return null;
    }

    private Object tryCallMethod(Object target, String methodName) {
        try {
            return callMethod(target, methodName);
        } catch (Throwable ignored) {
            // Method absent on this class, fall back to next strategy.
            return null;
        }
    }

    /**
     * Dismisses the shortcut menu after forwarding.
     */
    private void hideShortcutMenu(Activity launcher) {
        if (launcher == null) {
            return;
        }
        try {
            callMethod(launcher, "hideShortcutMenuWithoutAnim");
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "[menu] hide shortcut menu failed", t);
        }
    }
}
