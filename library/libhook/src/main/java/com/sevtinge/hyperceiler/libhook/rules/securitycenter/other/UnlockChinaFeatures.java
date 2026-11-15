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

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class UnlockChinaFeatures extends BaseHook {

    @Override
    public void init() {
        // 1. Spoof miui.os.Build.IS_INTERNATIONAL_BUILD to false inside SecurityCenter
        try {
            setStaticBooleanField(findClassIfExists("miui.os.Build"), "IS_INTERNATIONAL_BUILD", false);
        } catch (Throwable ignored) {
        }

        // 2. Spoof miui.os.Build.IS_GLOBAL_BUILD to false inside SecurityCenter
        try {
            setStaticBooleanField(findClassIfExists("miui.os.Build"), "IS_GLOBAL_BUILD", false);
        } catch (Throwable ignored) {
        }

        // 3. Hook Build.isInternationalBuild() if exists
        try {
            findAndHookMethod("miui.os.Build", "isInternationalBuild", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(false);
                }
            });
        } catch (Throwable ignored) {
        }

        // 4. Hook miui.os.MiuiInit.isResourceSubstitute if checked for CN features
        try {
            findAndHookMethod("miui.os.Build", "getRegion", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult("CN");
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
