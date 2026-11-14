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
package com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar

import android.graphics.Insets
import android.util.TypedValue
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClass
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

object StatusBarHorizontalPadding : BaseHook() {

    override fun init() {
        runCatching {
            loadClass("com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider")
                .findMethod { name("getStatusBarContentInsetsForCurrentRotation") }
                .createHook {
                    after { param ->
                        val leftDp = PrefsBridge.getInt("system_statusbar_horizmargin_left", 16)
                        val rightDp = PrefsBridge.getInt("system_statusbar_horizmargin_right", 16)

                        val rawInsets = param.result as? Insets ?: return@after
                        val context = mContext ?: return@after

                        val leftPx = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            leftDp.toFloat(),
                            context.resources.displayMetrics
                        ).toInt()

                        val rightPx = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            rightDp.toFloat(),
                            context.resources.displayMetrics
                        ).toInt()

                        param.result = Insets.of(leftPx, rawInsets.top, rightPx, rawInsets.bottom)
                    }
                }
        }
    }
}
