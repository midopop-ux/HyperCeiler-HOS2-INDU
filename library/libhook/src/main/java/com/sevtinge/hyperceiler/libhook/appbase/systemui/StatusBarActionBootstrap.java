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
package com.sevtinge.hyperceiler.libhook.appbase.systemui;

import static com.sevtinge.hyperceiler.libhook.base.BaseHook.getBooleanField;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Process;

import androidx.core.content.ContextCompat;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import io.github.libxposed.api.XposedInterface;

public class StatusBarActionBootstrap extends BaseHook {
    private static final String INTENT_CLEAR_MEMORY = "com.android.systemui.taskmanager.Clear";
    private static final String INTENT_SYSTEM_ACTION_RECENTS = "SYSTEM_ACTION_RECENTS";
    private static final String INTENT_SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String EXTRA_SHOW_TOAST = "show_toast";
    private static final String EXTRA_CLEAN_TYPE = "clean_type";
    private static final String EXTRA_PROTECTED_PACKAGES = "protected_pkgnames";
    private static final String EXTRA_EXPAND_ONLY = "expand_only";
    private static final String STATUS_BAR_SERVICE = "statusbar";

    private static WeakReference<Object> sStatusBarRef = new WeakReference<>(null);
    private static WeakReference<Object> sAutoBrightnessControllerRef = new WeakReference<>(null);
    private static volatile boolean sReceiverRegistered;

    @Override
    public void init() {
        findAndChainMethod("com.android.systemui.statusbar.phone.CentralSurfacesImpl", "start",
            new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    Object statusBar = chain.getThisObject();
                    sStatusBarRef = new WeakReference<>(statusBar);

                    Context context = (Context) getObjectField(statusBar, "mContext");
                    registerReceiver(context);
                    return result;
                }
            }
        );

        hookAllConstructors("com.android.systemui.controlcenter.policy.AutoBrightnessController", new io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook() {
            @Override
            public void after(io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam param) {
                sAutoBrightnessControllerRef = new WeakReference<>(param.getThisObject());
            }
        });
    }

    private void registerReceiver(Context context) {
        if (context == null || sReceiverRegistered) {
            return;
        }
        synchronized (StatusBarActionBootstrap.class) {
            if (sReceiverRegistered) {
                return;
            }
            ContextCompat.registerReceiver(
                context,
                new UnifiedReceiver(),
                createIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            );
            sReceiverRegistered = true;
        }
    }

    private IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter();
        // Only register actions with implemented receiver handling.
        // Add future actions here when UnifiedReceiver gains the matching logic.
        filter.addAction(StatusBarActionBridge.ACTION_OPEN_NOTIFICATION_CENTER);
        filter.addAction(StatusBarActionBridge.ACTION_OPEN_CONTROL_CENTER);
        filter.addAction(StatusBarActionBridge.ACTION_OPEN_RECENTS);
        filter.addAction(StatusBarActionBridge.ACTION_OPEN_VOLUME_DIALOG);
        filter.addAction(StatusBarActionBridge.ACTION_CLEAR_MEMORY);
        filter.addAction(StatusBarActionBridge.ACTION_RESTART_SYSTEM_UI);
        filter.addAction(StatusBarActionBridge.ACTION_TOGGLE_FEATURE);
        return filter;
    }

    private final class UnifiedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (StatusBarActionBridge.ACTION_CLEAR_MEMORY.equals(action)) {
                handleClearMemory(context);
            } else if (StatusBarActionBridge.ACTION_OPEN_CONTROL_CENTER.equals(action)) {
                handleOpenControlCenter(context);
            } else if (StatusBarActionBridge.ACTION_OPEN_RECENTS.equals(action)) {
                handleOpenRecents(context);
            } else if (StatusBarActionBridge.ACTION_OPEN_VOLUME_DIALOG.equals(action)) {
                handleOpenVolumeDialog(context);
            } else if (StatusBarActionBridge.ACTION_OPEN_NOTIFICATION_CENTER.equals(action)) {
                handleOpenNotificationCenter(context, intent);
            } else if (StatusBarActionBridge.ACTION_RESTART_SYSTEM_UI.equals(action)) {
                handleRestartSystemUI();
            } else if (StatusBarActionBridge.ACTION_TOGGLE_FEATURE.equals(action)) {
                String spec = intent.getStringExtra(StatusBarActionBridge.EXTRA_SPEC);
                if (spec != null) {
                    handleToggleFeature(context, spec);
                }
            }
        }
    }

    private void handleClearMemory(Context context) {
        Intent intent = new Intent(INTENT_CLEAR_MEMORY);
        intent.putExtra(EXTRA_SHOW_TOAST, true);
        intent.putExtra(EXTRA_CLEAN_TYPE, 1);
        intent.putStringArrayListExtra(EXTRA_PROTECTED_PACKAGES, new ArrayList<>());
        context.sendBroadcast(intent);
    }

    private void handleOpenControlCenter(Context context) {
        Object statusBar = sStatusBarRef.get();
        if (statusBar == null) {
            expandSettingsFallback(context);
            return;
        }

        try {
            Object commandQueue = getObjectField(statusBar, "mCommandQueue");
            if (commandQueue != null) {
                callMethod(commandQueue, "animateExpandSettingsPanel", (Object) null);
                return;
            }
        } catch (Throwable ignored) {
        }

        expandSettingsFallback(context);
    }

    private void handleOpenRecents(Context context) {
        Intent intent = new Intent(INTENT_SYSTEM_ACTION_RECENTS);
        intent.setPackage(INTENT_SYSTEMUI_PACKAGE);
        context.sendBroadcast(intent);
    }

    private void handleOpenVolumeDialog(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI
                );
            }
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(), "OpenVolumeDialog failed", t);
        }
    }

    private void handleOpenNotificationCenter(Context context, Intent intent) {
        Object statusBar = sStatusBarRef.get();
        if (statusBar == null) {
            expandNotificationsFallback(context);
            return;
        }

        try {
            Object panel = getObjectField(statusBar, "mNotificationPanel");
            boolean panelExpanded = getBooleanField(panel, "mPanelExpanded");
            boolean qsExpanded = getBooleanField(panel, "mQsExpanded");
            boolean expandOnly = intent.getBooleanExtra(EXTRA_EXPAND_ONLY, false);

            if (panelExpanded) {
                if (!expandOnly) {
                    if (qsExpanded) {
                        callMethod(statusBar, "closeQs");
                    } else {
                        callMethod(statusBar, "animateCollapsePanels");
                    }
                }
            } else {
                callMethod(statusBar, "animateExpandNotificationsPanel");
            }
        } catch (Throwable t) {
            expandNotificationsFallback(context);
        }
    }

    @SuppressWarnings("WrongConstant")
    private void expandNotificationsFallback(Context context) {
        long token = Binder.clearCallingIdentity();
        try {
            Object statusBarService = context.getSystemService(STATUS_BAR_SERVICE);
            if (statusBarService != null) {
                callMethod(statusBarService, "expandNotificationsPanel");
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @SuppressWarnings("WrongConstant")
    private void expandSettingsFallback(Context context) {
        long token = Binder.clearCallingIdentity();
        try {
            Object statusBarService = context.getSystemService(STATUS_BAR_SERVICE);
            if (statusBarService != null) {
                callMethod(statusBarService, "expandSettingsPanel", (Object) null);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void handleRestartSystemUI() {
        Process.sendSignal(Process.myPid(), Process.SIGNAL_KILL);
    }

    private static boolean sTorchEnabled = false;

    private void handleToggleFeature(Context context, String spec) {
        try {
            android.content.res.Resources modRes = com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.AppsTool.getModuleRes(context);
            switch (spec) {
                case "autobrightness" -> {
                    Object autoBrightnessController = sAutoBrightnessControllerRef.get();
                    if (autoBrightnessController == null) {
                        autoBrightnessController = getDependency(context.getClassLoader(), "com.android.systemui.controlcenter.policy.AutoBrightnessController");
                    }
                    if (autoBrightnessController != null) {
                        boolean enabled = (boolean) getBooleanField(autoBrightnessController, "enabled");
                        callMethod(autoBrightnessController, "toggleAutoBrightness");
                        showToast(context, modRes.getString(enabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_autobright_off : com.sevtinge.hyperceiler.libhook.R.string.toggle_autobright_on));
                    } else {
                        // Fallback
                        int mode = android.provider.Settings.System.getInt(context.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                        boolean isAuto = mode == android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
                        android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                                isAuto ? android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL : android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                        showToast(context, modRes.getString(isAuto ? com.sevtinge.hyperceiler.libhook.R.string.toggle_autobright_off : com.sevtinge.hyperceiler.libhook.R.string.toggle_autobright_on));
                    }
                }
                case "rotation" -> {
                    int current = android.provider.Settings.System.getInt(context.getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                    if (current == 0) {
                        android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 1);
                        showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_autorotate_on));
                    } else {
                        try {
                            android.view.WindowManager wm = (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                            if (wm != null) {
                                android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.USER_ROTATION, wm.getDefaultDisplay().getRotation());
                            }
                        } catch (Throwable ignored) {
                        }
                        android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                        showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_autorotate_off));
                    }
                }
                case "flashlight" -> {
                    boolean handled = false;
                    try {
                        Object fc = getDependency(context.getClassLoader(), "com.android.systemui.statusbar.policy.FlashlightController");
                        if (fc != null) {
                            boolean isEnabled = (boolean) callMethod(fc, "isEnabled");
                            callMethod(fc, "setFlashlight", !isEnabled);
                            showToast(context, modRes.getString(!isEnabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_flash_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_flash_off));
                            handled = true;
                        }
                    } catch (Throwable ignored) {
                    }
                    if (!handled) {
                        try {
                            android.hardware.camera2.CameraManager cm = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                            if (cm != null) {
                                String[] ids = cm.getCameraIdList();
                                if (ids != null && ids.length > 0) {
                                    sTorchEnabled = !sTorchEnabled;
                                    cm.setTorchMode(ids[0], sTorchEnabled);
                                    showToast(context, modRes.getString(sTorchEnabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_flash_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_flash_off));
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                case "wifi" -> {
                    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        boolean enabled = wm.isWifiEnabled();
                        callMethod(wm, "setWifiEnabled", !enabled);
                        showToast(context, modRes.getString(!enabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_wifi_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_wifi_off));
                    }
                }
                case "bt" -> {
                    android.bluetooth.BluetoothAdapter ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                    if (ba != null) {
                        boolean enabled = ba.isEnabled();
                        if (enabled) ba.disable(); else ba.enable();
                        showToast(context, modRes.getString(!enabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_bt_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_bt_off));
                    }
                }
                case "gps" -> {
                    boolean handled = false;
                    try {
                        Object lc = getDependency(context.getClassLoader(), "com.android.systemui.statusbar.policy.LocationController");
                        if (lc != null) {
                            boolean isEnabled = (boolean) callMethod(lc, "isLocationEnabled");
                            callMethod(lc, "setLocationEnabled", !isEnabled);
                            showToast(context, modRes.getString(!isEnabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_gps_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_gps_off));
                            handled = true;
                        }
                    } catch (Throwable ignored) {
                    }
                    if (!handled) {
                        int mode = android.provider.Settings.Secure.getInt(context.getContentResolver(), android.provider.Settings.Secure.LOCATION_MODE, android.provider.Settings.Secure.LOCATION_MODE_OFF);
                        boolean isOff = mode == android.provider.Settings.Secure.LOCATION_MODE_OFF;
                        android.provider.Settings.Secure.putInt(context.getContentResolver(), android.provider.Settings.Secure.LOCATION_MODE,
                                isOff ? android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : android.provider.Settings.Secure.LOCATION_MODE_OFF);
                        showToast(context, modRes.getString(isOff ? com.sevtinge.hyperceiler.libhook.R.string.toggle_gps_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_gps_off));
                    }
                }
                case "hotspot" -> {
                    boolean handled = false;
                    try {
                        Object hc = getDependency(context.getClassLoader(), "com.android.systemui.statusbar.policy.HotspotController");
                        if (hc != null) {
                            boolean isEnabled = (boolean) callMethod(hc, "isHotspotEnabled");
                            callMethod(hc, "setHotspotEnabled", !isEnabled);
                            showToast(context, modRes.getString(!isEnabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_hotspot_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_hotspot_off));
                            handled = true;
                        }
                    } catch (Throwable ignored) {
                    }
                    if (!handled) {
                        showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_hotspot_off));
                    }
                }
                case "mute" -> {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        int ringer = am.getRingerMode();
                        if (ringer == AudioManager.RINGER_MODE_NORMAL) {
                            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                            showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_sound_vibrate));
                        } else if (ringer == AudioManager.RINGER_MODE_VIBRATE) {
                            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                            showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_sound_silent));
                        } else {
                            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                            showToast(context, modRes.getString(com.sevtinge.hyperceiler.libhook.R.string.toggle_sound_normal));
                        }
                    }
                }
                case "night" -> {
                    android.provider.Settings.System.putInt(context.getContentResolver(), "dark_mode_enable_by_setting", 1);
                    android.app.UiModeManager umm = (android.app.UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
                    if (umm != null) {
                        boolean nightMode = umm.getNightMode() == android.app.UiModeManager.MODE_NIGHT_YES;
                        try {
                            callMethod(umm, "setNightModeActivated", !nightMode);
                        } catch (Throwable t) {
                            umm.setNightMode(!nightMode ? android.app.UiModeManager.MODE_NIGHT_YES : android.app.UiModeManager.MODE_NIGHT_NO);
                        }
                        showToast(context, modRes.getString(!nightMode ? com.sevtinge.hyperceiler.libhook.R.string.toggle_night_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_night_off));
                    }
                }
                case "dnd" -> {
                    Object statusBar = sStatusBarRef.get();
                    boolean handled = false;
                    if (statusBar != null) {
                        try {
                            Object iconPolicy = getObjectField(statusBar, "mIconPolicy");
                            Object zenController = getObjectField(iconPolicy, "mZenController");
                            if (zenController != null) {
                                boolean isZen = (boolean) callMethod(zenController, "isZenModeOn");
                                callMethod(zenController, "setZen", isZen ? 0 : 1, "DNDTile");
                                showToast(context, modRes.getString(!isZen ? com.sevtinge.hyperceiler.libhook.R.string.toggle_dnd_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_dnd_off));
                                handled = true;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (!handled) {
                        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) {
                            int filter = nm.getCurrentInterruptionFilter();
                            boolean isDnd = filter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                            nm.setInterruptionFilter(isDnd ? android.app.NotificationManager.INTERRUPTION_FILTER_ALL : android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                            showToast(context, modRes.getString(!isDnd ? com.sevtinge.hyperceiler.libhook.R.string.toggle_dnd_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_dnd_off));
                        }
                    }
                }
                case "nfc" -> {
                    android.nfc.NfcAdapter nfc = android.nfc.NfcAdapter.getDefaultAdapter(context);
                    if (nfc != null) {
                        boolean enabled = nfc.isEnabled();
                        if (enabled) {
                            callMethod(nfc, "disable");
                        } else {
                            callMethod(nfc, "enable");
                        }
                        showToast(context, modRes.getString(!enabled ? com.sevtinge.hyperceiler.libhook.R.string.toggle_nfc_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_nfc_off));
                    }
                }
                case "cell" -> {
                    android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm != null) {
                        boolean isData = tm.isDataEnabled();
                        try {
                            callMethod(tm, "setDataEnabledForReason", 0, !isData);
                        } catch (Throwable t) {
                            callMethod(tm, "setDataEnabled", !isData);
                        }
                        showToast(context, modRes.getString(!isData ? com.sevtinge.hyperceiler.libhook.R.string.toggle_mobiledata_on : com.sevtinge.hyperceiler.libhook.R.string.toggle_mobiledata_off));
                    }
                }
            }
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(), "handleToggleFeature failed: " + spec, t);
        }
    }

    private static Object getDependency(ClassLoader classLoader, String className) {
        try {
            Class<?> dependencyClass = findClassIfExists("com.android.systemui.Dependency", classLoader);
            if (dependencyClass != null) {
                Object sDependency = getStaticObjectField(dependencyClass, "sDependency");
                if (sDependency != null) {
                    Class<?> targetClass = findClassIfExists(className, classLoader);
                    if (targetClass != null) {
                        return callMethod(sDependency, "getDependencyInner", targetClass);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void showToast(Context context, String text) {
        if (text == null || text.isEmpty()) return;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    android.widget.Toast.makeText(context.getApplicationContext() != null ? context.getApplicationContext() : context, text, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    try {
                        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
