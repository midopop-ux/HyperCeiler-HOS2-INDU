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

import android.view.WindowManager;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class DisableFullScreenBackGesture extends BaseHook {
    private static final String TAG = "DisableFullScreenBackGesture";

    @Override
    public void init() {
        try {
            findAndHookMethodReplace("com.miui.launcher.utils.NotesGestureCompat", "supportsGestureHeightExpansion", new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }

        // Hook GestureStubView.injectBackKeyEvent to completely prevent back event from left edge stub
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "injectBackKeyEvent", boolean.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object stub = param.getThisObject();
                        int pos = getIntField(stub, "mGestureStubPos");
                        // 0 = left edge stub
                        if (pos == 0) {
                            XposedLog.d(TAG, "Blocked back key injection from left edge stub");
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Error checking mGestureStubPos: " + t.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook GestureStubView.injectBackKeyEvent: " + t.getMessage());
        }

        // Hook GestureStubView.onTouchEvent to completely consume/ignore touch events on left edge stub
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "onTouchEvent", android.view.MotionEvent.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object stub = param.getThisObject();
                        int pos = getIntField(stub, "mGestureStubPos");
                        if (pos == 0) {
                            // Left edge: ignore touch event completely so it does not trigger gesture
                            param.setResult(false);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook GestureStubView.onTouchEvent: " + t.getMessage());
        }

        // Hook GestureStubView.shockByBackGesture to prevent haptic feedback on left edge
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "shockByBackGesture", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object stub = param.getThisObject();
                        int pos = getIntField(stub, "mGestureStubPos");
                        if (pos == 0) {
                            param.setResult(null);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // Hook GestureStubView.updateGestureTouchHeight: set height to 0 for left edge stub so it doesn't intercept touch
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "updateGestureTouchHeight", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        Object stub = param.getThisObject();
                        int pos = getIntField(stub, "mGestureStubPos");
                        if (pos == 0) {
                            setIntField(stub, "mGestureTouchHeight", 0);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // Hook GestureStubView.getGestureStubWindowParam: collapse window height for left edge
        try {
            findAndHookMethod("com.miui.home.recents.GestureStubView", "getGestureStubWindowParam", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        Object stub = param.getThisObject();
                        int pos = getIntField(stub, "mGestureStubPos");
                        if (pos == 0) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.getResult();
                            if (lp != null) {
                                lp.height = 0;
                                lp.width = 0;
                                param.setResult(lp);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // Hook GesturesBackTouchProcessor: disable left edge detection and back arrow
        try {
            Class<?> processorCls = findClassIfExists("com.miui.home.recents.GesturesBackTouchProcessor");
            if (processorCls != null) {
                findAndHookMethod(processorCls, "setGestureEdgeWidth", int.class, int.class, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        // Force left edge width to 0
                        param.getArgs()[0] = 0;
                    }
                });

                findAndHookMethod(processorCls, "isTriggerBack", float.class, int.class, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        // arg 1 is gesture edge direction (0 = left)
                        int edge = (int) param.getArgs()[1];
                        if (edge == 0) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });

                findAndHookMethod(processorCls, "isShowBackArrow", float.class, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        try {
                            int leftEdge = getIntField(param.getThisObject(), "mGestureEdgeLeft");
                            if (leftEdge <= 0) {
                                param.setResult(Boolean.FALSE);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook GesturesBackTouchProcessor: " + t.getMessage());
        }
    }
}
