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
package com.sevtinge.hyperceiler.libhook.rules.home.navigation;

import static com.sevtinge.hyperceiler.libhook.utils.api.DeviceHelper.Miui.isPad;

import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.appbase.mihome.HomeBaseHookNew;
import com.sevtinge.hyperceiler.libhook.appbase.mihome.Version;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class BackGestureAreaHeight extends HomeBaseHookNew {

    @Version(isPad = true)
    private void initPadHook() {
        findAndHookMethod("com.miui.home.recents.GestureStubView", "getGestureStubWindowParam", new IMethodHook() {
            @Override
            public void after(final HookParam param) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.getResult();
                int pct = PrefsBridge.getInt("home_navigation_back_area_height", 60);
                lp.height = Math.round(lp.height / 100.0f * pct);
                lp.width = Math.round(lp.width / 100.0f * pct);
                param.setResult(lp);
            }
        });
    }

    @Override
    public void initBase() {
        // 1. Restrict the Window touchable region strictly to the middle third of the screen
        // Top boundary: totalHeight / 3; Bottom boundary: 2 * totalHeight / 3
        // This leaves the top third and bottom third 100% free for application navigation drawers
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "updateTouchRegion",
                "com.android.systemui.shared.recents.touchableRegion.InternalInsetsInfoCompat", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        View view = (View) param.getThisObject();
                        int mRotation = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mRotation");
                        int mScreenHeight = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenHeight");
                        int mScreenWidth = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenWidth");
                        int totalHeight = (mRotation == 0 || mRotation == 2) ? mScreenHeight : mScreenWidth;
                        if (totalHeight <= 0) return;

                        int top = totalHeight / 3;
                        int bottom = (totalHeight * 2) / 3;

                        Region region = new Region();
                        region.set(view.getLeft(), top, view.getLeft() + view.getWidth(), bottom);

                        Object inoutInfo = param.getArgs()[0];
                        com.sevtinge.hyperceiler.libhook.base.BaseHook.callMethod(inoutInfo, "setTouchableInsets", 3);
                        com.sevtinge.hyperceiler.libhook.base.BaseHook.callMethod(inoutInfo, "setTouchableRegion", region);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}

        // 2. View-level touch event filter: redirect any touch outside the middle third to underlying app
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "shouldRedirectEvent", MotionEvent.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    MotionEvent event = (MotionEvent) param.getArgs()[0];
                    if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                        View view = (View) param.getThisObject();
                        int mRotation = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mRotation");
                        int mScreenHeight = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenHeight");
                        int mScreenWidth = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenWidth");
                        int totalHeight = (mRotation == 0 || mRotation == 2) ? mScreenHeight : mScreenWidth;
                        if (totalHeight <= 0) return;

                        float y = event.getRawY();
                        float top = totalHeight / 3f;
                        float bottom = (totalHeight * 2f) / 3f;

                        if (y < top || y > bottom) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {}

        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "onTouchEvent", MotionEvent.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    MotionEvent event = (MotionEvent) param.getArgs()[0];
                    if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                        View view = (View) param.getThisObject();
                        int mRotation = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mRotation");
                        int mScreenHeight = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenHeight");
                        int mScreenWidth = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(view, "mScreenWidth");
                        int totalHeight = (mRotation == 0 || mRotation == 2) ? mScreenHeight : mScreenWidth;
                        if (totalHeight <= 0) return;

                        float y = event.getRawY();
                        float top = totalHeight / 3f;
                        float bottom = (totalHeight * 2f) / 3f;

                        if (y < top || y > bottom) {
                            try {
                                com.sevtinge.hyperceiler.libhook.base.BaseHook.callMethod(view, "redirectEvent", event);
                            } catch (Throwable ignored) {}
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {}

        // 3. Disable full-screen height expansion so NotesGesture does not stretch touch region across full screen
        try {
            findAndHookMethodReplace("com.miui.launcher.utils.NotesGestureCompat", "supportsGestureHeightExpansion", new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    return false;
                }
            });
        } catch (Throwable ignored) {}

        // 4. Update touch height calculation
        try {
            findAndReplaceMethod("com.miui.home.recents.GestureStubView", "updateGestureTouchHeight", new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    Object obj = param.getThisObject();
                    int mRotation = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(obj, "mRotation");
                    int mScreenHeight = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(obj, "mScreenHeight");
                    int mScreenWidth = com.sevtinge.hyperceiler.libhook.base.BaseHook.getIntField(obj, "mScreenWidth");
                    int totalHeight = (mRotation == 0 || mRotation == 2) ? mScreenHeight : mScreenWidth;
                    com.sevtinge.hyperceiler.libhook.base.BaseHook.setIntField(obj, "mGestureTouchHeight", totalHeight / 3);
                    return null;
                }
            });
        } catch (Throwable ignored) {}
    }
}
