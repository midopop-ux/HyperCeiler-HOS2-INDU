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

public class ThemeServerSwitch extends BaseHook {

    private static final String GLOBAL_HOST = "api.zhuti.intl.xiaomi.com";
    private static final String CHINA_HOST = "api.zhuti.xiaomi.com";

    @Override
    public void init() {
        // 1. Hook RequestUrl in ThemeManager network stack
        IMethodHook urlRedirectHook = new IMethodHook() {
            @Override
            public void before(HookParam param) {
                Object[] args = param.getArgs();
                if (args != null && args.length > 0 && args[0] instanceof String url) {
                    if (url.contains(GLOBAL_HOST)) {
                        param.setArgs(new Object[]{url.replace(GLOBAL_HOST, CHINA_HOST)});
                    }
                }
            }
        };

        try {
            findAndHookMethod("com.thememanager.network.RequestUrl", "setBaseUrl", String.class, urlRedirectHook);
        } catch (Throwable ignored) {
        }

        try {
            findAndHookMethod("com.android.thememanager.controller.online.RequestUrl", "setBaseUrl", String.class, urlRedirectHook);
        } catch (Throwable ignored) {
        }

        // 2. Hook OkHttp Request.Builder / HttpUrl if used
        try {
            findAndHookMethod("okhttp3.HttpUrl", "parse", String.class, urlRedirectHook);
        } catch (Throwable ignored) {
        }
    }
}
