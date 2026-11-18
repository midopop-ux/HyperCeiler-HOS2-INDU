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
package com.sevtinge.hyperceiler.libhook.rules.home.navigation;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class ThirdPartyLauncherGestureFix extends BaseHook {

    private static final String TAG = "ThirdPartyLauncherGestureFix";

    @Override
    public void init() {
        IReplaceHook returnTrueHook = new IReplaceHook() {
            @Override
            public Object replace(HookParam param) {
                return Boolean.TRUE;
            }
        };

        // 1. Hook BuildConfigUtils in POCO Launcher / MIUI Home to bypass UI checks and settings dialogs
        try {
            findAndHookMethodReplace("com.miui.home.common.utils.BuildConfigUtils", "isUsePocoHomeAsCurrentDefaultHome", returnTrueHook);
            findAndHookMethodReplace("com.miui.home.common.utils.BuildConfigUtils", "isUsePocoHomeAsDefaultHome", Context.class, returnTrueHook);
            findAndHookMethodReplace("com.miui.home.common.utils.BuildConfigUtils", "isUseMiuiHomeAsDefaultHome", Context.class, returnTrueHook);
            XposedLog.d(TAG, "Hooked BuildConfigUtils default home checks successfully");
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook BuildConfigUtils methods: " + t.getMessage());
        }

        // 2. Hook BaseRecentsImpl to force default home state to true so gesture windows and stubs are kept alive
        try {
            findAndHookMethod("com.miui.home.recents.BaseRecentsImpl", "setIsUseMiuiHomeAsDefaultHome", boolean.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.getArgs()[0] = Boolean.TRUE;
                }
            });
            findAndHookMethodReplace("com.miui.home.recents.BaseRecentsImpl", "getIsUseMiuiHomeAsDefaultHome", returnTrueHook);
            findAndHookMethodReplace("com.miui.home.recents.BaseRecentsImpl", "isUseLauncherRecentsAndFsGesture", returnTrueHook);
            XposedLog.d(TAG, "Hooked BaseRecentsImpl default home checks successfully");
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook BaseRecentsImpl methods: " + t.getMessage());
        }

        // 2b. Hook RecentsActivity to auto-finish and remove task when stopped so it never lingers above third-party home (Nova)
        try {
            findAndHookMethod("com.miui.home.recents.RecentsActivity", "onStop", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        android.app.Activity activity = (android.app.Activity) param.getThisObject();
                        if (isThirdPartyLauncherDefault(activity)) {
                            activity.finishAndRemoveTask();
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Failed to finish RecentsActivity onStop: " + t.getMessage());
                    }
                }
            });
            XposedLog.d(TAG, "Hooked RecentsActivity.onStop successfully");
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook RecentsActivity.onStop: " + t.getMessage());
        }

        // 3. Hook NavStubView for full gesture support (Home & Recents) when a third-party launcher (Nova) is default
        try {
            Class<?> navStubViewCls = findClassIfExists("com.miui.home.recents.NavStubView");
            if (navStubViewCls != null) {
                // Allow entering hold state even if recents remote anim hasn't started
                hookAllMethods(navStubViewCls, "isAllowToAppHoldState", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        try {
                            Context ctx = (Context) callMethod(param.getThisObject(), "getContext");
                            if (isThirdPartyLauncherDefault(ctx)) {
                                param.setResult(Boolean.TRUE);
                            }
                        } catch (Throwable ignored) {}
                    }
                });

                // In actionUpResolution, when third-party launcher is default, trigger Home or Recents
                hookAllMethods(navStubViewCls, "actionUpResolution", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object navStub = param.getThisObject();
                        try {
                            Context ctx = (Context) callMethod(navStub, "getContext");
                            if (!isThirdPartyLauncherDefault(ctx)) {
                                return;
                            }
                            int speedAndDir = (int) callMethod(navStub, "getActionUpSpeedAndDirection");
                            boolean isAppHold = getBooleanField(navStub, "mIsAppHold");
                            boolean isPause = false;
                            try {
                                isPause = (boolean) callMethod(navStub, "isPauseWhileSwipeUp");
                            } catch (Throwable ignored) {}

                            // Speed 5 = Fast pull up, 10 = Normal pull up / safe area release, 2 = Drag up
                            if (speedAndDir == 5 || speedAndDir == 10 || speedAndDir == 2) {
                                if (isAppHold || isPause) {
                                    callMethod(navStub, "enterAppHoldState");
                                } else {
                                    callMethod(navStub, "performAppToHome");
                                }
                                setBooleanField(navStub, "mIsAppHold", false);
                            }
                        } catch (Throwable t) {
                            XposedLog.w(TAG, "Error in actionUpResolution hook: " + t.getMessage());
                        }
                    }
                });

                // Also hook performHomeToHome as safety net
                hookAllMethods(navStubViewCls, "performHomeToHome", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object navStub = param.getThisObject();
                        try {
                            Context ctx = (Context) callMethod(navStub, "getContext");
                            if (!isThirdPartyLauncherDefault(ctx)) {
                                return;
                            }
                            callMethod(navStub, "injectKeyEventDownAndUp", 3);
                            callMethod(navStub, "finishRecentsActivityDirectly");
                        } catch (Throwable t) {
                            XposedLog.w(TAG, "Error in performHomeToHome hook: " + t.getMessage());
                        }
                    }
                });

                // Also hook performHomeToRecents as safety net
                hookAllMethods(navStubViewCls, "performHomeToRecents", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object navStub = param.getThisObject();
                        try {
                            Context ctx = (Context) callMethod(navStub, "getContext");
                            if (!isThirdPartyLauncherDefault(ctx)) {
                                return;
                            }
                            callMethod(navStub, "injectKeyEventDownAndUp", 187);
                            callMethod(navStub, "finishRecentsActivityDirectly");
                        } catch (Throwable t) {
                            XposedLog.w(TAG, "Error in performHomeToRecents hook: " + t.getMessage());
                        }
                    }
                });
                XposedLog.d(TAG, "Hooked NavStubView gesture resolutions successfully");
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook NavStubView: " + t.getMessage());
        }
    }

    private static boolean isThirdPartyLauncherDefault(Context context) {
        if (context == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return !context.getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
