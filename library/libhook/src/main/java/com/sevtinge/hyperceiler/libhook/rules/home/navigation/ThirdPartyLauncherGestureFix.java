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

        // 3. Hook OverviewComponentObserver to maintain full screen gesture targets and stubs
        try {
            findAndHookMethodReplace("com.miui.home.recents.OverviewComponentObserver", "isHomeAndOverviewSame", returnTrueHook);

            findAndHookMethod("com.miui.home.recents.OverviewComponentObserver", "updateOverviewTargets", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        Object observer = param.getThisObject();
                        setBooleanField(observer, "mIsHomeAndOverviewSame", true);

                        Class<?> recentsClass = findClassIfExists("com.miui.home.recents.RecentsModel");
                        if (recentsClass != null) {
                            Object recentsModelInstance = callStaticMethod(recentsClass, "getInstance");
                            if (recentsModelInstance != null) {
                                Object baseRecents = callMethod(recentsModelInstance, "getRecentsImpl");
                                if (baseRecents != null) {
                                    callMethod(baseRecents, "setIsUseMiuiHomeAsDefaultHome", true);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Error in OverviewComponentObserver.updateOverviewTargets hook: " + t.getMessage());
                    }
                }
            });
            XposedLog.d(TAG, "Hooked OverviewComponentObserver methods successfully");
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook OverviewComponentObserver.updateOverviewTargets: " + t.getMessage());
        }

    }
}
