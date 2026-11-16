package com.sevtinge.hyperceiler.libhook.rules.systemframework.navbar

import android.content.Context
import android.os.Bundle
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.appbase.systemframework.GlobalActionBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam
import io.github.lingqiqi5211/ezhooktool.xposed.java.IMethodHook

class NavigationButtonLongPress : BaseHook() {

    override fun init() {
        if (!PrefsBridge.getBoolean("system_ui_navigation_custom")) {
            return
        }

        findAndHookMethod("com.android.server.policy.MiuiSingleKeyRule", "getFunction", String::class.java, object : IMethodHook {
            override fun before(param: HookParam) {
                val action = param.args[0] as? String ?: return
                if (action == "long_press_back_key") {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_back_long_press_action", 0) > 0) {
                        param.result = "custom_nav_back_long"
                    }
                } else if (action == "long_press_home_key") {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_home_long_press_action", 0) > 0) {
                        param.result = "custom_nav_home_long"
                    }
                } else if (action == "long_press_menu_key") {
                    if (PrefsBridge.getInt("prefs_key_navigation_bar_menu_long_press_action", 0) > 0) {
                        param.result = "custom_nav_menu_long"
                    }
                }
            }
        })

        findAndHookMethod(
            "com.miui.server.input.util.ShortCutActionsUtils",
            "triggerFunction",
            String::class.java,
            String::class.java,
            Bundle::class.java,
            java.lang.Boolean.TYPE,
            object : IMethodHook {
                override fun before(param: HookParam) {
                    val action = param.args[0] as? String ?: return
                    var prefKey: String? = null
                    if (action == "custom_nav_back_long") {
                        prefKey = "prefs_key_navigation_bar_back_long_press"
                    } else if (action == "custom_nav_home_long") {
                        prefKey = "prefs_key_navigation_bar_home_long_press"
                    } else if (action == "custom_nav_menu_long") {
                        prefKey = "prefs_key_navigation_bar_menu_long_press"
                    }

                    if (prefKey != null) {
                        val context = getObjectField(param.thisObject, "mContext") as? Context
                        if (context != null) {
                            GlobalActionBridge.handleAction(context, prefKey)
                            param.result = java.lang.Boolean.TRUE
                        }
                    }
                }
            }
        )
    }
}
