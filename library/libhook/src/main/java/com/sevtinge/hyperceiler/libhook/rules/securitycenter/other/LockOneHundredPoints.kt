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
package com.sevtinge.hyperceiler.libhook.rules.securitycenter.other

import android.view.View
import com.sevtinge.hyperceiler.common.log.XposedLog
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClass
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

object LockOneHundredPoints : BaseHook() {
    override fun useDexKit() = true

    override fun initDexKit(): Boolean {
        // Fallback DexKit lookup for older SecurityCenter versions
        runCatching {
            scoreOld
            score
            score3
            addRiskApp
        }
        return true
    }

    private val scoreOld by lazy<Method?> {
        runCatching {
            requiredMember("LockOneHundredPoints1") {
                it.findClass {
                    matcher {
                        className = "com.miui.securityscan.scanner.ScoreManager"
                    }
                }.findMethod {
                    matcher {
                        addUsingString("getMinusPredictScore", StringMatchType.Contains)
                        returnType = "int"
                    }
                }.single()
            }
        }.getOrNull()
    }

    private val score by lazy<Method?> {
        runCatching {
            requiredMember("LockOneHundredPoints2") {
                it.findClass {
                    matcher {
                        className = "com.miui.securityscan.scanner.ScoreManager"
                    }
                }.findMethod {
                    matcher {
                        usingNumbers(41, 100, 0)
                        returnType = "int"
                    }
                }.single()
            }
        }.getOrNull()
    }

    private val score3 by lazy<Method?> {
        runCatching {
            requiredMember("LockOneHundredPointsField") {
                it.findClass {
                    matcher {
                        className = "com.miui.securityscan.scanner.ScoreManager"
                    }
                }.findMethod {
                    matcher {
                        addUsingField {
                            type = "java.util.Map"
                        }
                        addInvoke {
                            addUsingField {
                                type = "int"
                            }
                            returnType = "int"
                        }
                    }
                }.single()
            }
        }.getOrNull()
    }

    private val addRiskApp by lazy<Method?> {
        runCatching {
            requiredMember("AddRiskApp") {
                it.findClass {
                    matcher {
                        className = "com.miui.securityscan.scanner.ScoreManager"
                    }
                }.findMethod {
                    matcher {
                        addInvoke("Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z")
                        paramCount = 1
                        returnType = "void"
                    }
                }.single()
            }
        }.getOrNull()
    }

    override fun init() {
        // Disable "Optimize" button click
        runCatching {
            loadClass("com.miui.securityscan.ui.main.MainContentFrame")
                .findMethod { name("onClick"); parameterTypes(View::class.java) }
                .createHook {
                    returnConstant(null)
                }
        }

        // Primary Direct Hook for HyperOS 2 / newer SecurityCenter (v13.x / v12.x)
        val scoreManagerClass = findClassIfExists("com.miui.securityscan.scanner.ScoreManager")
        if (scoreManagerClass != null) {
            // Hook main score getters to always return 100
            for (methodName in arrayOf("i", "r", "getScore", "getPredictScore")) {
                runCatching {
                    val m = scoreManagerClass.getDeclaredMethod(methodName)
                    m.isAccessible = true
                    hookMethod(m, object : io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook() {
                        override fun before(param: io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam) {
                            param.result = 100
                        }
                    })
                }
            }

            // Hook minus / penalty score getters to return 0
            for (methodName in arrayOf("p", "k", "getMinusScore", "getMinusPredictScore")) {
                runCatching {
                    val m = scoreManagerClass.getDeclaredMethod(methodName)
                    m.isAccessible = true
                    hookMethod(m, object : io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook() {
                        override fun before(param: io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam) {
                            param.result = 0
                        }
                    })
                }
            }

            // Hook virus / risk count getter y() to return 0
            runCatching {
                val m = scoreManagerClass.getDeclaredMethod("y")
                m.isAccessible = true
                hookMethod(m, object : io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook() {
                    override fun before(param: io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam) {
                        param.result = 0
                    }
                })
            }
        }

        // Fallback: Legacy DexKit hook for older SecurityCenter versions
        runCatching {
            scoreOld?.createHook {
                returnConstant(0)
            }
            score?.createHook {
                returnConstant(100)
            }
            score3?.createHook {
                returnConstant(true)
            }
            addRiskApp?.createHook {
                returnConstant(null)
            }
        }.onFailure {
            XposedLog.e(TAG, lpparam.packageName, "LockOneHundredPoints legacy hook Failed: $it")
        }
    }
}
