/*
  * This file is part of HyperCeiler.

  * HyperCeiler is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as
  * published by the Free Software Foundation, either version 3 of the
  * License.

  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.

  * You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <https://www.gnu.org/licenses/>.

  * Copyright (C) 2023-2026 HyperCeiler Contributions
*/
package com.sevtinge.hyperceiler.libhook.rules.securitycenter.app;

import android.view.View;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class AppDefaultSort extends BaseHook {

    private static final String TAG = "AppDefaultSort";

    @Override
    public void init() {
        int order = PrefsBridge.getStringAsInt("security_center_app_default_sort", 0);
        if (order <= 0) {
            return;
        }
        final int targetSort = order - 1;

        Class<?> manageFragCls = findClassIfExists("com.miui.appmanager.fragment.ManageFragment");
        if (manageFragCls != null) {
            // Apply sort seamlessly after initial load without corrupting savedInstanceState or loaders
            hookAllMethods(manageFragCls, "onResume", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    final Object frag = param.getThisObject();
                    try {
                        View view = (View) callMethod(frag, "getView");
                        if (view != null) {
                            view.post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        int currentZ = getIntField(frag, "z");
                                        if (currentZ != targetSort) {
                                            setIntField(frag, "z", targetSort);
                                            Object adapter = getObjectField(frag, "f");
                                            if (adapter != null) {
                                                callMethod(adapter, "v", targetSort);
                                            }
                                            String[] s = (String[]) getObjectField(frag, "s");
                                            Object menu = getObjectField(frag, "B");
                                            if (menu != null && s != null && targetSort >= 0 && targetSort < s.length) {
                                                callMethod(menu, "n", (Object) s[targetSort]);
                                            }
                                            callMethod(frag, "updateData");
                                            XposedLog.d(TAG, "Applied default sort seamlessly: " + targetSort);
                                        }
                                    } catch (Throwable t) {
                                        XposedLog.w(TAG, "Error applying default sort in view.post: " + t.getMessage());
                                    }
                                }
                            });
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Error in ManageFragment.onResume hook: " + t.getMessage());
                    }
                }
            });
        }
    }
}
