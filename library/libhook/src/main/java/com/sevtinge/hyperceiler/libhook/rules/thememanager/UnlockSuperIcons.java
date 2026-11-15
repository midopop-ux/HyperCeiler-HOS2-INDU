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
package com.sevtinge.hyperceiler.libhook.rules.thememanager;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class UnlockSuperIcons extends BaseHook {

    @Override
    public void init() {
        // 1. Spoof IS_INTERNATIONAL_BUILD to false inside ThemeManager so it enables China store features
        try {
            setStaticBooleanField(findClassIfExists("miui.os.Build"), "IS_INTERNATIONAL_BUILD", false);
        } catch (Throwable ignored) {
        }

        // 2. Hook isInternationalBuild() methods if queried dynamically
        IMethodHook returnFalse = new IMethodHook() {
            @Override
            public void before(HookParam param) {
                param.setResult(false);
            }
        };

        try {
            findAndHookMethod("miui.os.Build", "isInternationalBuild", returnFalse);
        } catch (Throwable ignored) {
        }

        try {
            findAndHookMethod("com.android.thememanager.basemodule.utils.MiuiUtils", "isInternationalBuild", returnFalse);
        } catch (Throwable ignored) {
        }

        // 3. Force Large Icon support in ThemeManager
        IMethodHook returnTrue = new IMethodHook() {
            @Override
            public void before(HookParam param) {
                param.setResult(true);
            }
        };

        try {
            findAndHookMethod("com.android.thememanager.largeicon.LargeIconRuntimeManager", "supportLargeIcon", returnTrue);
        } catch (Throwable ignored) {
        }

        try {
            findAndHookMethod("com.android.thememanager.largeicon.LargeIconRuntimeManager", "isSupportLargeIcon", returnTrue);
        } catch (Throwable ignored) {
        }

        try {
            findAndHookMethod("com.android.thememanager.basemodule.utils.LargeIconHelper", "isSupportLargeIcon", returnTrue);
        } catch (Throwable ignored) {
        }
    }
}
