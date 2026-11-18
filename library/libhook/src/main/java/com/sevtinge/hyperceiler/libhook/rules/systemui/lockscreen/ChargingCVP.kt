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
package com.sevtinge.hyperceiler.libhook.rules.systemui.lockscreen

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Handler
import android.os.PowerManager
import android.util.ArrayMap
import android.view.Gravity
import android.widget.TextView
import com.sevtinge.hyperceiler.common.log.XposedLog
import java.lang.ref.WeakReference
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.lingqiqi5211.ezhooktool.core.callMethod
import io.github.lingqiqi5211.ezhooktool.core.callStaticMethod
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHooks
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectFieldOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getStaticObjectFieldOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.setObjectField
import java.io.BufferedReader
import java.io.FileReader
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object ChargingCVP : BaseHook() {
    private const val TAG = "ChargingCVP"

    private val isShowTemp: Boolean
        get() = PrefsBridge.getBoolean("system_ui_show_battery_temperature")

    private val isShowMoreC: Boolean
        get() = PrefsBridge.getBoolean("system_ui_show_charging_c_more")

    @Volatile
    private var isReceiverRegistered = false

    @Volatile
    private var indicationTextViewRef: WeakReference<TextView>? = null

    private fun getChargingOffsetY(context: Context): Float {
        val enabled = PrefsBridge.getBoolean("system_ui_lock_screen_charging_custom_y_enabled", true)
        if (!enabled) return 0f
        val offsetDp = PrefsBridge.getInt("system_ui_lock_screen_charging_offset_y", 135)
        val density = context.resources.displayMetrics.density
        return offsetDp * density
    }

    @SuppressLint("SetTextI18n")
    override fun init() {
        val clazzDependency = findClassIfExists("com.android.systemui.Dependency")
        val clazzKeyguardIndicationController =
            findClassIfExists("com.android.systemui.statusbar.KeyguardIndicationController")

        findClassIfExists("com.android.systemui.statusbar.phone.KeyguardIndicationTextView")?.constructors?.toList()?.createHooks {
            after { param ->
                val textView = param.thisObject as TextView
                indicationTextViewRef = WeakReference(textView)

                val offsetPx = getChargingOffsetY(textView.context)
                textView.apply {
                    isSingleLine = false
                    maxLines = 3
                    textSize = 10.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    paint.isFakeBoldText = true
                    lineSpacingMultiplier = 1.15f
                    gravity = Gravity.CENTER_HORIZONTAL
                    if (offsetPx > 0f) {
                        translationY = -offsetPx
                    }
                }

                textView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val currentOffset = getChargingOffsetY(v.context)
                    if (currentOffset > 0f && v.translationY != -currentOffset) {
                        v.translationY = -currentOffset
                    }
                }

                if (clazzDependency != null && clazzKeyguardIndicationController != null) {
                    setShowSpacing(clazzDependency, clazzKeyguardIndicationController, param)
                }
            }
        }

        // 修改底部文本信息，保留原有系统信息（如快充、百分比等），并追加实时温度与功率
        val clazzChargeUtils = findClassIfExists("com.miui.charge.ChargeUtils")
            ?: findClassIfExists("com.android.keyguard.charge.ChargeUtils")
        clazzChargeUtils?.findMethod { name("getChargingHintText"); paramCount(3) }
            ?.createHook {
                after { param ->
                    val originalText = param.result as? String
                    val temp = getTemp()
                    val cvp = getCVP()
                    if (originalText != null) {
                        val extra = StringBuilder()
                        if (temp.isNotEmpty()) {
                            extra.append(temp)
                        }
                        if (cvp.isNotEmpty()) {
                            if (extra.isNotEmpty()) extra.append(" · ")
                            extra.append(cvp)
                        }

                        if (extra.isNotEmpty()) {
                            val cleanOriginal = originalText.trim().trimEnd('·', ' ', '•')
                            param.result = "$cleanOriginal\n$extra"
                        }
                    }
                }
            }
    }

    private fun setShowSpacing(
        clazzDependency: Class<*>,
        clazzKeyguardIndicationController: Class<*>,
        param: HookParam
    ) {
        if (isReceiverRegistered) return
        synchronized(this) {
            if (isReceiverRegistered) return
            isReceiverRegistered = true
        }

        val textView = param.thisObject as? TextView ?: return
        val appContext = textView.context.applicationContext ?: textView.context

        val keyguardIndicationController = runCatching {
            clazzDependency.callStaticMethod("get", clazzKeyguardIndicationController)
        }.getOrNull() ?: runCatching {
            val clazzMiuiStub = findClassIfExists("miui.stub.MiuiStub")
            val instanceMiuiStub = clazzMiuiStub?.getStaticObjectFieldOrNull("INSTANCE")
            val mSysUIProvider = instanceMiuiStub?.getObjectFieldOrNull("mSysUIProvider")
            val mKeyguardIndicationController = mSysUIProvider?.getObjectFieldOrNull("mKeyguardIndicationController")
            mKeyguardIndicationController?.callMethod("get")
        }.getOrNull()

        if (keyguardIndicationController == null) {
            XposedLog.w(TAG, "KeyguardIndicationController not found, skip periodic refresh")
            return
        }

        val handler = Handler(appContext.mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                try {
                    doUpdateForHyperOS(keyguardIndicationController)
                } catch (t: Throwable) {
                    XposedLog.w(TAG, "Error in doUpdateForHyperOS: " + t.message)
                }

                // 刷新间隔：默认 1.0 秒 (1000ms)，若用户在设置中自定义了间距则采用设置值
                val intervalMs = if (PrefsBridge.getBoolean("system_ui_lock_screen_show_spacing_value")) {
                    val spacing = PrefsBridge.getInt("system_ui_lock_screen_show_spacing", 2)
                    (spacing * 500L).coerceAtLeast(500L)
                } else {
                    1000L
                }

                handler.postDelayed(this, intervalMs)
            }

            fun doUpdateForHyperOS(controller: Any) {
                runCatching {
                    val clazzMiuiDependency = findClassIfExists("com.miui.systemui.MiuiDependency") ?: return
                    val clazzMiuiChargeController = findClassIfExists("com.miui.charge.MiuiChargeController") ?: return
                    val sDependency = clazzMiuiDependency.getStaticObjectFieldOrNull("sDependency") ?: return
                    val mProviders = sDependency.getObjectFieldOrNull("mProviders") as? ArrayMap<*, *> ?: return
                    val mMiuiChargeControllerProvider = mProviders[clazzMiuiChargeController] ?: return

                    val instanceMiuiChargeController = runCatching {
                        mMiuiChargeControllerProvider.getObjectFieldOrNull("f$0")?.callMethod("get")
                    }.getOrNull() ?: runCatching {
                        mMiuiChargeControllerProvider.callMethod("get")
                    }.getOrNull() ?: return

                    val mBatteryStatus = instanceMiuiChargeController.getObjectFieldOrNull("mBatteryStatus") ?: return
                    val level = mBatteryStatus.getObjectFieldOrNull("level") ?: 0
                    val plugged = (mBatteryStatus.getObjectFieldOrNull("plugged") as? Int) ?: 0

                    val clazzMiuiBatteryStatus = findClassIfExists("com.miui.systemui.charge.MiuiBatteryStatus", lpparam.classLoader)
                    val isPluggedIn = runCatching {
                        if (clazzMiuiBatteryStatus != null) {
                            callStaticMethod(clazzMiuiBatteryStatus, "isPluggedIn", plugged) as? Boolean
                        } else null
                    }.getOrNull() ?: (plugged == 1 || plugged == 2 || plugged == 4)

                    val mContext = instanceMiuiChargeController.getObjectFieldOrNull("mContext")

                    val clazzChargeUtils =
                        findClassIfExists("com.miui.charge.ChargeUtils", lpparam.classLoader)
                            ?: findClassIfExists("com.android.keyguard.charge.ChargeUtils", lpparam.classLoader)
                    if (clazzChargeUtils != null) {
                        val chargingHintText = callStaticMethod(
                            clazzChargeUtils,
                            "getChargingHintText",
                            level,
                            isPluggedIn,
                            mContext
                        )
                        controller.setObjectField("mComputePowerIndication", chargingHintText)
                        controller.callMethod("updateDeviceEntryIndication", false)

                        val tv = indicationTextViewRef?.get()
                        if (tv != null) {
                            val offsetPx = getChargingOffsetY(tv.context)
                            if (offsetPx > 0f && tv.translationY != -offsetPx) {
                                tv.translationY = -offsetPx
                            }
                        }
                    }
                }
            }
        }

        val screenOnOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        handler.removeCallbacks(runnable)
                        handler.post(runnable)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(runnable)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        appContext.registerReceiver(screenOnOffReceiver, filter, Context.RECEIVER_EXPORTED)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == true) {
            handler.post(runnable)
        }
    }

    private fun getTemp(): String {
        var temp = 0.0
        try {
            BufferedReader(FileReader("/sys/class/power_supply/battery/temp")).use { reader ->
                temp = BigDecimal(reader.readLine().toDouble() / 10.0)
                    .setScale(1, RoundingMode.HALF_UP)
                    .toDouble()
            }
        } catch (_: Exception) {
            temp = 0.0
        }

        return if (isShowTemp && temp > 0.0) "$temp ℃" else ""
    }

    @SuppressLint("DefaultLocale")
    private fun getCVP(): String {
        runCatching {
            var current = 0.0
            var voltage = 0.0

            current = FileReader("/sys/class/power_supply/battery/current_now").use { fileReader ->
                BufferedReader(fileReader).use { bufferedReader ->
                    -1.0 * bufferedReader.readLine().toDouble() / 1000000.0
                }
            }
            voltage = FileReader("/sys/class/power_supply/battery/voltage_now").use { fileReader ->
                BufferedReader(fileReader).use { bufferedReader ->
                    bufferedReader.readLine().toDouble() / 1000000.0
                }
            }

            val absCurrent = abs(current)
            val watt = abs(current * voltage)
            val power = String.format("%.1f", watt)

            val mCurrent = if (isShowMoreC) {
                "${(absCurrent * 1000).toInt()} mA"
            } else {
                "${String.format("%.1f", absCurrent)} A"
            }

            if (current == 0.0) return ""

            return "$mCurrent ($power W)"
        }
        return ""
    }
}
