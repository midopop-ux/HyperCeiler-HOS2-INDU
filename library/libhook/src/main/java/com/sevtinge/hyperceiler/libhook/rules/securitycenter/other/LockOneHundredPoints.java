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
package com.sevtinge.hyperceiler.libhook.rules.securitycenter.other;

import android.view.View;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.lang.reflect.Method;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Locks SecurityCenter scan score at 100 points and disables deduction / risk penalties.
 * Compatible with HyperOS 2 / modern SecurityCenter (v13.x / v12.x) and legacy versions.
 */
public class LockOneHundredPoints extends BaseHook {

    @Override
    public void init() {
        // Disable "Optimize" button click
        try {
            findAndHookMethod("com.miui.securityscan.ui.main.MainContentFrame", "onClick", View.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        } catch (Throwable ignored) {
        }

        // Hook ScoreManager methods
        Class<?> scoreManagerClass = findClassIfExists("com.miui.securityscan.scanner.ScoreManager");
        if (scoreManagerClass != null) {
            // Hook main score getters to always return 100
            for (String methodName : new String[]{"i", "r", "getScore", "getPredictScore"}) {
                try {
                    Method m = scoreManagerClass.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    hookMethod(m, new IMethodHook() {
                        @Override
                        public void before(HookParam param) {
                            param.setResult(100);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }

            // Hook minus / penalty score getters to return 0
            for (String methodName : new String[]{"p", "k", "getMinusScore", "getMinusPredictScore"}) {
                try {
                    Method m = scoreManagerClass.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    hookMethod(m, new IMethodHook() {
                        @Override
                        public void before(HookParam param) {
                            param.setResult(0);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }

            // Hook virus / risk count getter y() to return 0
            try {
                Method m = scoreManagerClass.getDeclaredMethod("y");
                m.setAccessible(true);
                hookMethod(m, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        param.setResult(0);
                    }
                });
            } catch (Throwable ignored) {
            }

            // Hook addRiskApp to ignore adding any risk apps
            try {
                for (Method m : scoreManagerClass.getDeclaredMethods()) {
                    if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                        m.setAccessible(true);
                        hookMethod(m, new IMethodHook() {
                            @Override
                            public void before(HookParam param) {
                                param.setResult(null);
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
