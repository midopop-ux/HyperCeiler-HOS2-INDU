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

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Directly controls autostart toggle for the viewed app in App Info (AMAppInfomationActivity).
 * Reads and sets MIUI AppOps OP_AUTO_START (10008) directly into system server.
 */
public class ShowAutostartToggle extends BaseHook {

    private static final int OP_AUTO_START = 10008;
    private static final String AUTOSTART_KEY = "app_autostart_pref";

    private Class<?> mAmAppInfoCls;

    @Override
    public void init() {
        // Hook PreferenceGroup.removePreference to prevent SecurityCenter from hiding app_autostart_pref on Global/HyperOS
        Class<?> prefGroupCls = findClassIfExists("androidx.preference.PreferenceGroup");
        Class<?> prefCls = findClassIfExists("androidx.preference.Preference");
        if (prefGroupCls != null && prefCls != null) {
            findAndHookMethod(prefGroupCls, "removePreference", prefCls, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object preference = param.getArgs()[0];
                    if (preference != null) {
                        try {
                            String key = (String) callMethod(preference, "getKey");
                            if (AUTOSTART_KEY.equals(key)) {
                                param.setResult(false);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        }

        // Hook Activity onCreate for both HyperOS and older MIUI
        String[] targetActivities = new String[]{
                "com.miui.appmanager.ApplicationsDetailsActivity",
                "com.miui.appmanager.AMAppInfomationActivity"
        };
        for (String actName : targetActivities) {
            Class<?> actCls = findClassIfExists(actName);
            if (actCls != null) {
                findAndHookMethod(actCls, "onCreate", Bundle.class, new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        Activity activity = (Activity) param.getThisObject();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> setupAutostartPreference(activity), 350);
                    }
                });
            }
        }
    }

    private void setupAutostartPreference(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            Object fragment = findFragment(activity);
            if (fragment == null) return;

            PackageInfo packageInfo = getPackageInfo(fragment);
            if (packageInfo == null || packageInfo.applicationInfo == null) return;

            final String pkgName = packageInfo.packageName;
            final int uid = packageInfo.applicationInfo.uid;
            final Context context = activity.getApplicationContext();
            final AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

            Object preference = callMethod(fragment, "findPreference", AUTOSTART_KEY);
            if (preference == null) {
                // If not found in default layout, create and add a CheckBoxPreference / SwitchPreference
                preference = createAndAddAutostartPreference(fragment, activity, pkgName, uid, appOps);
            }

            if (preference != null) {
                // Force visibility to true
                try {
                    callMethod(preference, "setVisible", true);
                } catch (Throwable ignored) {
                }

                // Check actual system state for OP_AUTO_START
                boolean isAllowed = isAutostartAllowed(context, appOps, uid, pkgName);
                try {
                    callMethod(preference, "setChecked", isAllowed);
                } catch (Throwable ignored) {
                }

                // Hook preference change listener to immediately affect system
                hookPreferenceChangeListener(preference, context, appOps, uid, pkgName);
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "setupAutostartPreference error", t);
        }
    }

    private Object findFragment(Activity act) {
        try {
            if (act instanceof androidx.fragment.app.FragmentActivity) {
                androidx.fragment.app.FragmentManager fm = ((androidx.fragment.app.FragmentActivity) act).getSupportFragmentManager();
                for (androidx.fragment.app.Fragment f : fm.getFragments()) {
                    if (f != null && (f.getClass().getSimpleName().contains("ApplicationsDetails") || f.getClass().getSimpleName().contains("AppInfo") || f.getClass().getSimpleName().contains("Details"))) {
                        return f;
                    }
                }
                if (!fm.getFragments().isEmpty()) {
                    return fm.getFragments().get(0);
                }
            }
        } catch (Throwable ignored) {
        }
        Object frag = act.getFragmentManager().findFragmentById(android.R.id.content);
        if (frag != null) return frag;
        try {
            Field f = findFirstFieldByExactType(act.getClass(), findClassIfExists("androidx.fragment.app.Fragment"));
            if (f != null) {
                f.setAccessible(true);
                return f.get(act);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private PackageInfo getPackageInfo(Object fragment) {
        try {
            Field piField = findFirstFieldByExactType(fragment.getClass(), PackageInfo.class);
            if (piField != null) {
                piField.setAccessible(true);
                return (PackageInfo) piField.get(fragment);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isAutostartAllowed(Context ctx, AppOpsManager appOps, int uid, String pkg) {
        try {
            // Check AppOpsManager mode for OP_AUTO_START (10008)
            Method checkOpMethod = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            int mode = (int) checkOpMethod.invoke(appOps, OP_AUTO_START, uid, pkg);
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            try {
                // Fallback via android.miui.AppOpsUtils
                Class<?> utilsCls = Class.forName("android.miui.AppOpsUtils");
                Method getMethod = utilsCls.getMethod("getApplicationAutoStart", Context.class, String.class);
                int mode = (int) getMethod.invoke(null, ctx, pkg);
                return mode == 0; // 0 = MODE_ALLOWED
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private void setAutostartAllowed(Context ctx, AppOpsManager appOps, int uid, String pkg, boolean allow) {
        int targetMode = allow ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED;
        try {
            Method setModeMethod = AppOpsManager.class.getMethod("setMode", int.class, int.class, String.class, int.class);
            setModeMethod.invoke(appOps, OP_AUTO_START, uid, pkg, targetMode);
            XposedLog.i(TAG, getPackageName(), "Set autostart for " + pkg + " -> " + allow);
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "Failed to setMode via AppOpsManager", t);
        }

        try {
            // Also notify android.miui.AppOpsUtils to keep system and MIUI security in full sync
            Class<?> utilsCls = Class.forName("android.miui.AppOpsUtils");
            Method setMethod = utilsCls.getMethod("setApplicationAutoStart", Context.class, String.class, boolean.class);
            setMethod.invoke(null, ctx, pkg, allow);
        } catch (Throwable ignored) {
        }
    }

    private void hookPreferenceChangeListener(Object preference, Context ctx, AppOpsManager appOps, int uid, String pkg) {
        try {
            Class<?> changeListenerCls = findClassIfExists("androidx.preference.Preference$OnPreferenceChangeListener");
            if (changeListenerCls == null) {
                changeListenerCls = findClassIfExists("android.preference.Preference$OnPreferenceChangeListener");
            }

            if (changeListenerCls != null) {
                Object listener = java.lang.reflect.Proxy.newProxyInstance(
                        changeListenerCls.getClassLoader(),
                        new Class<?>[]{changeListenerCls},
                        (proxy, method, args) -> {
                            if ("onPreferenceChange".equals(method.getName()) && args != null && args.length > 1) {
                                boolean newState = Boolean.TRUE.equals(args[1]);
                                setAutostartAllowed(ctx, appOps, uid, pkg, newState);
                                return true;
                            }
                            return false;
                        });
                callMethod(preference, "setOnPreferenceChangeListener", listener);
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "hookPreferenceChangeListener error", t);
        }
    }

    private Object createAndAddAutostartPreference(Object fragment, Activity activity, String pkg, int uid, AppOpsManager appOps) {
        try {
            ClassLoader cl = fragment.getClass().getClassLoader();
            Class<?> prefCls = findClassIfExists("miuix.preference.CheckBoxPreference", cl);
            if (prefCls == null) {
                prefCls = findClassIfExists("androidx.preference.CheckBoxPreference", cl);
            }
            if (prefCls == null) return null;

            Object pref = newInstance(prefCls, activity);
            callMethod(pref, "setKey", AUTOSTART_KEY);

            // Fetch MIUI localized string for "Autostart"
            int strId = activity.getResources().getIdentifier("activity_autostart_title", "string", activity.getPackageName());
            if (strId == 0) {
                strId = activity.getResources().getIdentifier("app_autostart_pref", "string", activity.getPackageName());
            }
            if (strId != 0) {
                callMethod(pref, "setTitle", activity.getString(strId));
            } else {
                callMethod(pref, "setTitle", "Autostart");
            }

            // Find category to add to
            Object screen = callMethod(fragment, "getPreferenceScreen");
            if (screen != null) {
                String[] permCategories = new String[]{
                        "app_detail_perm_category",
                        "app_permissions_category",
                        "permission_category",
                        "category_permissions"
                };
                Object category = null;
                for (String catKey : permCategories) {
                    category = callMethod(screen, "findPreference", catKey);
                    if (category != null) break;
                }
                if (category == null) {
                    // Try getting field n (which is the permissions PreferenceCategory in ApplicationsDetailsFragment)
                    try {
                        category = getObjectField(fragment, "n");
                    } catch (Throwable ignored) {
                    }
                }
                if (category != null) {
                    callMethod(category, "addPreference", pref);
                } else {
                    callMethod(screen, "addPreference", pref);
                }
            }
            return pref;
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "createAndAddAutostartPreference error", t);
            return null;
        }
    }
}
