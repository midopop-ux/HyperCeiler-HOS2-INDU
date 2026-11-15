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
package com.sevtinge.hyperceiler.libhook.rules.systemui.controlcenter

import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.utils.api.DeviceHelper.System.isMoreAndroidVersion
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClass
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createBeforeHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHooks
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.setObjectField

object ControlCenterStyle : BaseHook() {
    override fun init() {
        val freezeOld = PrefsBridge.getBoolean("system_control_center_freeze_old")

        if (isMoreAndroidVersion(36)) {
            loadClass("com.miui.systemui.controlcenter.data.repository.ControlCenterSettingsRepositoryImpl")
                .constructors.toList().createHooks {
                    after {
                        it.thisObject.setObjectField("forceUseControlCenter", false)
                    }
                }
        } else {
            // Hook useControlCenterObserver before it runs in ControlCenterControllerImpl.<init>
            runCatching {
                loadClass("com.android.systemui.controlcenter.policy.ControlCenterControllerImpl\$useControlCenterObserver\$1")
                    .declaredConstructors.toList().createHooks {
                        before {
                            val parent = it.args.firstOrNull { arg ->
                                arg?.javaClass?.name?.contains("ControlCenterControllerImpl") == true
                            }
                            if (parent != null) {
                                parent.setObjectField("forceUseControlCenterPanel", false)
                            }
                        }
                    }
            }

            // Hook ControlCenterControllerImpl constructor
            loadClass("com.android.systemui.controlcenter.policy.ControlCenterControllerImpl")
                .declaredConstructors.toList().createHooks {
                    after {
                        it.thisObject.setObjectField("forceUseControlCenterPanel", false)
                        if (freezeOld) {
                            it.thisObject.setObjectField("useControlCenter", false)
                        }
                    }
                }

            // If freeze old is enabled, also force isUseControlCenter to return false
            if (freezeOld) {
                runCatching {
                    loadClass("com.android.systemui.controlcenter.policy.ControlCenterControllerImpl")
                        .findMethod { name("isUseControlCenter") }
                        .createBeforeHook {
                            it.result = false
                        }
                }

                // Intercept putIntForUser so neither SystemUI nor anything else can set use_control_panel to 1
                runCatching {
                    loadClass("com.android.systemui.util.settings.UserSettingsProxy")
                        .declaredMethods.filter { it.name == "putIntForUser" }.createHooks {
                            before {
                                val name = it.args.firstOrNull { arg -> arg == "use_control_panel" }
                                if (name != null) {
                                    // Always force value to 0
                                    it.args.forEachIndexed { index, arg ->
                                        if (arg is Int && (arg == 1 || arg == 0) && index > 0) {
                                            it.args[index] = 0
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}
