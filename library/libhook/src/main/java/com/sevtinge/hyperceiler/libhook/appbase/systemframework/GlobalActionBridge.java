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
package com.sevtinge.hyperceiler.libhook.appbase.systemframework;

import static java.lang.System.currentTimeMillis;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.KeyEvent;

import com.sevtinge.hyperceiler.common.log.AndroidLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.appbase.systemframework.actions.HomeNativeGestureActions;
import com.sevtinge.hyperceiler.libhook.appbase.systemui.StatusBarActionBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

public final class GlobalActionBridge {
    private static final int ACTION_ID_NONE = 0;
    private static final int ACTION_ID_NOTIFICATION_CENTER = 1;
    private static final int ACTION_ID_CLEAR_MEMORY = 2;
    private static final int ACTION_ID_INVERT_COLORS = 3;
    private static final int ACTION_ID_LOCK_SCREEN = 4;
    private static final int ACTION_ID_GO_TO_SLEEP = 5;
    private static final int ACTION_ID_SCREENSHOT = 6;
    private static final int ACTION_ID_RECENTS = 7;
    private static final int ACTION_ID_VOLUME_DIALOG = 8;
    private static final int ACTION_ID_VOLUME_UP = 9;
    private static final int ACTION_ID_VOLUME_DOWN = 10;
    private static final int ACTION_ID_POWER_MENU = 12;
    private static final int ACTION_ID_LAUNCH_APP = 13;
    private static final int ACTION_ID_GO_HOME = 14;
    private static final int ACTION_ID_CONTROL_CENTER = 15;
    private static final int ACTION_ID_SUPER_XIAOAI = 16;
    private static final int ACTION_ID_SUPER_XIAOAI_SCREEN_RECOGNIZER = 17;
    private static final int ACTION_ID_GOOGLE_CIRCLE_TO_SEARCH = 18;
    private static final int ACTION_ID_FORCE_STOP_TOP_APP = 19;
    private static final int ACTION_ID_GOOGLE_VOICE_ASSISTANT = 20;
    private static final int ACTION_ID_SWITCH_TO_PREV_APP = 11;
    private static final int ACTION_ID_GO_BACK = 21;
    private static final int ACTION_ID_SCROLL_TO_TOP = 25;
    private static final int ACTION_ID_MEDIA_KEY_MIN = 85;
    private static final int ACTION_ID_MEDIA_KEY_MAX = 88;
    private static final int ACTION_ID_LAUNCH_SHORTCUT = 22;
    private static final int ACTION_ID_LAUNCH_ACTIVITY = 23;
    private static final int ACTION_ID_TOGGLE_FEATURE = 24;

    public static final String ACTION_TOGGLE_COLOR_INVERSION = BaseHook.ACTION_PREFIX + "ToggleColorInversion";
    public static final String ACTION_LOCK_SCREEN = BaseHook.ACTION_PREFIX + "LockScreen";
    public static final String ACTION_GO_TO_SLEEP = BaseHook.ACTION_PREFIX + "GoToSleep";
    public static final String ACTION_GO_HOME = BaseHook.ACTION_PREFIX + "GoHome";
    public static final String ACTION_FORCE_STOP_TOP_APP = BaseHook.ACTION_PREFIX + "ForceStopTopApp";
    public static final String ACTION_SCREEN_CAPTURE = BaseHook.ACTION_PREFIX + "ScreenCapture";
    public static final String ACTION_OPEN_POWER_MENU = BaseHook.ACTION_PREFIX + "OpenPowerMenu";
    public static final String ACTION_LAUNCH_INTENT = BaseHook.ACTION_PREFIX + "LaunchIntent";
    public static final String ACTION_RESTART_APPS = BaseHook.ACTION_PREFIX + "RestartApps";

    private GlobalActionBridge() {
    }

    public static boolean handleAction(Context context, String key) {
        return handleAction(context, key, false);
    }

    public static boolean handleAction(Context context, String key, boolean skipLock) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        int action = PrefsBridge.getInt(key + "_action", ACTION_ID_NONE);
        if (action <= ACTION_ID_NONE) {
            return false;
        }
        if (handleMediaAction(context, action)) {
            return true;
        }
        if (handleSystemUiAction(context, action)) {
            return true;
        }
        if (handleHomeNativeAction(context, key, action)) {
            return true;
        }
        if (handleSystemFrameworkAction(context, action)) {
            return true;
        }
        if (action == ACTION_ID_LAUNCH_APP) {
            return launchAppIntent(context, key, skipLock);
        }
        if (action == ACTION_ID_LAUNCH_SHORTCUT) {
            return launchShortcutIntent(context, key, skipLock);
        }
        if (action == ACTION_ID_LAUNCH_ACTIVITY) {
            return launchActivityIntent(context, key, skipLock);
        }
        if (action == ACTION_ID_TOGGLE_FEATURE) {
            return toggleFeature(context, key);
        }
        return false;
    }

    public static boolean sendAction(Context context, String actionSuffix) {
        try {
            context.sendBroadcast(new Intent(BaseHook.ACTION_PREFIX + actionSuffix));
            return true;
        } catch (Throwable t) {
            AndroidLog.w("GlobalActionBridge", "system", "sendAction", t);
            return false;
        }
    }

    private static boolean handleMediaAction(Context context, int action) {
        if (action < ACTION_ID_MEDIA_KEY_MIN || action > ACTION_ID_MEDIA_KEY_MAX) {
            return false;
        }
        if (isMediaActionsAllowed(context)) {
            sendDownUpKeyEvent(context, action, false);
        }
        return true;
    }

    private static boolean handleSystemUiAction(Context context, int action) {
        return switch (action) {
            case ACTION_ID_NOTIFICATION_CENTER -> StatusBarActionBridge.openNotificationCenter(context);
            case ACTION_ID_CLEAR_MEMORY -> StatusBarActionBridge.clearMemory(context);
            case ACTION_ID_RECENTS -> StatusBarActionBridge.openRecents(context);
            case ACTION_ID_VOLUME_DIALOG -> StatusBarActionBridge.openVolumeDialog(context);
            case ACTION_ID_VOLUME_UP -> adjustVolume(context, AudioManager.ADJUST_RAISE);
            case ACTION_ID_VOLUME_DOWN -> adjustVolume(context, AudioManager.ADJUST_LOWER);
            case ACTION_ID_CONTROL_CENTER -> StatusBarActionBridge.openControlCenter(context);
            default -> false;
        };
    }

    private static boolean adjustVolume(Context context, int direction) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                return true;
            }
        } catch (Throwable t) {
            AndroidLog.w("GlobalActionBridge", "system", "adjustVolume", t);
        }
        return false;
    }

    private static boolean handleHomeNativeAction(Context context, String key, int action) {
        return switch (action) {
            case ACTION_ID_LOCK_SCREEN -> HomeNativeGestureActions.lockScreen(context) || sendAction(context, "LockScreen");
            case ACTION_ID_SUPER_XIAOAI -> HomeNativeGestureActions.launchSuperXiaoAi(context);
            case ACTION_ID_SUPER_XIAOAI_SCREEN_RECOGNIZER -> HomeNativeGestureActions.launchSuperXiaoAiScreenRecognizer(context);
            case ACTION_ID_GOOGLE_CIRCLE_TO_SEARCH -> launchGoogleCircleToSearch(context);
            case ACTION_ID_GOOGLE_VOICE_ASSISTANT -> HomeNativeGestureActions.launchGoogleVoiceAssistant(context);
            default -> false;
        };
    }

    private static boolean handleSystemFrameworkAction(Context context, int action) {
        return switch (action) {
            case ACTION_ID_INVERT_COLORS -> sendAction(context, "ToggleColorInversion");
            case ACTION_ID_GO_TO_SLEEP -> sendAction(context, "GoToSleep");
            case ACTION_ID_SCREENSHOT -> sendAction(context, "ScreenCapture");
            case ACTION_ID_POWER_MENU -> sendAction(context, "OpenPowerMenu");
            case ACTION_ID_GO_HOME -> sendAction(context, "GoHome");
            case ACTION_ID_FORCE_STOP_TOP_APP -> sendAction(context, "ForceStopTopApp");
            case ACTION_ID_SWITCH_TO_PREV_APP -> sendAction(context, "SwitchToPrevApp");
            case ACTION_ID_GO_BACK -> sendAction(context, "GoBack");
            case ACTION_ID_SCROLL_TO_TOP -> sendAction(context, "ScrollToTop");
            default -> false;
        };
    }

    private static boolean launchGoogleCircleToSearch(Context context) {
        if (HomeNativeGestureActions.launchGoogleCircleToSearchFromHome(context)) {
            return true;
        }
        if (sendAction(context, "StartGoogleCircleToSearch")) {
            return true;
        }
        return HomeNativeGestureActions.triggerCircleToSearchViaVoiceInteraction();
    }

    public static boolean launchAppIntent(Context context, String key, boolean skipLock) {
        return launchIntent(context, getIntent(context, key, IntentType.APP, skipLock));
    }

    public static boolean launchShortcutIntent(Context context, String key, boolean skipLock) {
        return launchIntent(context, getIntent(context, key, IntentType.SHORTCUT, skipLock));
    }

    public static boolean launchActivityIntent(Context context, String key, boolean skipLock) {
        return launchIntent(context, getIntent(context, key, IntentType.ACTIVITY, skipLock));
    }

    public static boolean toggleFeature(Context context, String key) {
        String tileSpec = PrefsBridge.getString(key + "_toggle_spec", null);
        if (tileSpec != null && !tileSpec.isEmpty()) {
            Intent intent = new Intent(BaseHook.ACTION_PREFIX + "ClickTile");
            intent.putExtra("spec", tileSpec);
            context.sendBroadcast(intent);
            return true;
        }

        int toggleId = PrefsBridge.getInt(key + "_toggle", 1);
        String spec = switch (toggleId) {
            case 1 -> "wifi";
            case 2 -> "bt";
            case 3 -> "gps";
            case 4 -> "nfc";
            case 5 -> "mute";
            case 6 -> "autobrightness";
            case 7 -> "rotation";
            case 8 -> "flashlight";
            case 9 -> "cell";
            case 10 -> "hotspot";
            case 11 -> "dnd";
            case 12 -> "night";
            default -> null;
        };
        if (spec != null) {
            Intent intent = new Intent(BaseHook.ACTION_PREFIX + "ClickTile");
            intent.putExtra("spec", spec);
            context.sendBroadcast(intent);
            return true;
        }
        return false;
    }

    public static boolean launchIntent(Context context, Intent intent) {
        if (intent == null) {
            return false;
        }
        Intent broadcastIntent = new Intent(ACTION_LAUNCH_INTENT);
        broadcastIntent.putExtra("intent", intent);
        context.sendBroadcast(broadcastIntent);
        return true;
    }

    public static Intent getIntent(Context context, String prefs, IntentType intentType, boolean skipLock) {
        try {
            if (intentType == IntentType.APP) {
                prefs += "_app";
            } else if (intentType == IntentType.ACTIVITY) {
                prefs += "_activity";
            } else if (intentType == IntentType.SHORTCUT) {
                prefs += "_shortcut_intent";
            }

            String prefValue = PrefsBridge.getString(prefs, null);
            if (prefValue == null) {
                return null;
            }

            Intent intent = null;
            if (intentType == IntentType.SHORTCUT) {
                intent = Intent.parseUri(prefValue, 0);
            } else {
                String[] pkgAppArray = prefValue.split("\\|");
                if (pkgAppArray.length == 0 || pkgAppArray[0] == null || pkgAppArray[0].isEmpty()) {
                    return null;
                }
                String packageName = pkgAppArray[0];
                String activityName = (pkgAppArray.length >= 2 && pkgAppArray[1] != null && !pkgAppArray[1].isEmpty() && !"null".equals(pkgAppArray[1]))
                    ? pkgAppArray[1] : null;

                if (intentType == IntentType.APP) {
                    if (activityName == null) {
                        intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    } else {
                        intent = new Intent();
                        intent.setComponent(new ComponentName(packageName, activityName));
                    }
                } else if (intentType == IntentType.ACTIVITY) {
                    if (activityName == null) {
                        return null;
                    }
                    intent = new Intent();
                    intent.setComponent(new ComponentName(packageName, activityName));
                }

                if (intent == null) {
                    return null;
                }

                int user = PrefsBridge.getInt(prefs + "_user", 0);
                if (user != 0) {
                    intent.putExtra("user", user);
                }
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            if (intentType == IntentType.APP) {
                intent.setAction(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
            }

            if (skipLock) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("ShowCameraWhenLocked", true);
                intent.putExtra("StartActivityWhenLocked", true);
            }

            return intent;
        } catch (Throwable t) {
            AndroidLog.w("GlobalActionBridge", "system", "getIntent", t);
            return null;
        }
    }

    public static boolean isMediaActionsAllowed(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean isAllowed = audioManager.isMusicActive()
            || (Boolean) BaseHook.callMethod(audioManager, "isMusicActiveRemotely");
        if (!isAllowed) {
            long currentTime = currentTimeMillis();
            long lastPauseTime = Settings.System.getLong(
                context.getContentResolver(),
                "last_music_paused_time",
                currentTime
            );
            if (currentTime - lastPauseTime < 10 * 60 * 1000) {
                isAllowed = true;
            }
        }
        return isAllowed;
    }

    public static void sendDownUpKeyEvent(Context context, int keyCode, boolean vibrate) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));

        if (vibrate && PrefsBridge.getBoolean("controls_volumemedia_vibrate", true)) {
            // Reserved for future vibration feedback implementation.
        }
    }

    public enum IntentType {
        APP,
        ACTIVITY,
        SHORTCUT
    }
}
