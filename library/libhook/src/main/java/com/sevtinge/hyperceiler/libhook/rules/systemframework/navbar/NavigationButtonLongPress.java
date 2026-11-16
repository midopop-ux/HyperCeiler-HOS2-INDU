package com.sevtinge.hyperceiler.libhook.rules.systemframework.navbar;

import android.content.Context;
import android.os.Bundle;

import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.appbase.systemframework.GlobalActionBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class NavigationButtonLongPress extends BaseHook {

    @Override
    public void init() {
        if (!PrefsBridge.getBoolean("system_ui_navigation_custom")) {
            return;
        }

        findAndHookMethod("com.android.server.policy.MiuiSingleKeyRule", "getFunction", String.class, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                String action = (String) param.getArgs()[0];
                if (action == null) return;

                if ("long_press_back_key".equals(action)) {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_back_long_press_action", 0) > 0) {
                        param.setResult("custom_nav_back_long");
                    }
                } else if ("long_press_home_key".equals(action)) {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_home_long_press_action", 0) > 0) {
                        param.setResult("custom_nav_home_long");
                    }
                } else if ("long_press_menu_key".equals(action)) {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_menu_long_press_action", 0) > 0) {
                        param.setResult("custom_nav_menu_long");
                    }
                }
            }
        });

        findAndHookMethod("com.miui.server.input.util.ShortCutActionsUtils", "triggerFunction",
                String.class, String.class, Bundle.class, boolean.class, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                String action = (String) param.getArgs()[0];
                if (action == null) return;

                String prefKey = null;
                if ("custom_nav_back_long".equals(action)) {
                    prefKey = "prefs_key_navigation_bar_back_long_press";
                } else if ("custom_nav_home_long".equals(action)) {
                    prefKey = "prefs_key_navigation_bar_home_long_press";
                } else if ("custom_nav_menu_long".equals(action)) {
                    prefKey = "prefs_key_navigation_bar_menu_long_press";
                }

                if (prefKey != null) {
                    Context context = (Context) getObjectField(param.getThisObject(), "mContext");
                    if (context != null) {
                        GlobalActionBridge.handleAction(context, prefKey);
                        param.setResult(Boolean.TRUE);
                    }
                }
            }
        });
    }
}
