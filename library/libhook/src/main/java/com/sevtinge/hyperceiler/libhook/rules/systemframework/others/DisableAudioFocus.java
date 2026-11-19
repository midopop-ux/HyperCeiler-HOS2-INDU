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

package com.sevtinge.hyperceiler.libhook.rules.systemframework.others;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.Log;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class DisableAudioFocus extends BaseHook {

    private static final String TAG = "DisableAudioFocus";
    private static final String AUDIO_SERVICE = "com.android.server.audio.AudioService";
    private static final String MEDIA_FOCUS_CONTROL = "com.android.server.audio.MediaFocusControl";
    private static final String FOCUS_REQUESTER = "com.android.server.audio.FocusRequester";
    private static final String PLAYBACK_ACTIVITY_MONITOR = "com.android.server.audio.PlaybackActivityMonitor";
    private static final String DUCKING_MANAGER = "com.android.server.audio.PlaybackActivityMonitor$DuckingManager";
    private static final String DUCKED_APP = "com.android.server.audio.PlaybackActivityMonitor$DuckingManager$DuckedApp";
    private static final String FADE_OUT_MANAGER = "com.android.server.audio.FadeOutManager";
    private static final String PAM_STUB = "com.android.server.audio.PlaybackActivityMonitorStub";
    private static final String PAM_STUB_IMPL = "com.android.server.audio.PlaybackActivityMonitorStubImpl";

    @Override
    public void init() {
        Log.i(TAG, "Initializing DisableAudioFocus in " + getPackageName());
        try {
            IReplaceHook returnTrueReplaceHook = new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    return Boolean.TRUE;
                }
            };

            IMethodHook returnTrueMethodHook = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(Boolean.TRUE);
                }
            };

            IMethodHook returnFalseMethodHook = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(Boolean.FALSE);
                }
            };

            IMethodHook noOpMethodHook = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            };

            IMethodHook returnZeroMethodHook = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(0);
                }
            };

            // 1. Force mMultiAudioFocusEnabled = true on MediaFocusControl instantiation
            hookAllConstructors(MEDIA_FOCUS_CONTROL, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        setBooleanField(param.getThisObject(), "mMultiAudioFocusEnabled", true);
                        Context context = (Context) getObjectField(param.getThisObject(), "mContext");
                        if (context != null) {
                            Settings.System.putInt(context.getContentResolver(), "multi_audio_focus_enabled", 1);
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Error configuring MediaFocusControl instance: " + t.getMessage());
                    }
                }
            });

            // 2. Grant audio focus unconditionally on every focus request
            hookAllMethods(MEDIA_FOCUS_CONTROL, "requestAudioFocus", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        setBooleanField(param.getThisObject(), "mMultiAudioFocusEnabled", true);
                    } catch (Throwable ignored) {}
                    param.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                }
            });

            // 3. Prevent system/apps from disabling multi audio focus
            hookAllMethods(MEDIA_FOCUS_CONTROL, "updateMultiAudioFocus", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    if (args != null && args.length > 0) {
                        args[0] = Boolean.TRUE;
                    }
                }
            });

            // 4. Force getMultiAudioFocusEnabled() & canReassignAudioFocus() to return true
            findAndHookMethodReplace(MEDIA_FOCUS_CONTROL, "getMultiAudioFocusEnabled", returnTrueReplaceHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "canReassignAudioFocus", returnTrueMethodHook);

            // 5. Block sending focus loss and call audio muting in MediaFocusControl
            hookAllMethods(MEDIA_FOCUS_CONTROL, "propagateFocusLossFromGain_syncAf", noOpMethodHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "propagateFocusLossFromGain_syncAf_mi", noOpMethodHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "runAudioCheckerForRingOrCallAsync", noOpMethodHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "mutePlayersForCall", noOpMethodHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "duckPlayers", returnFalseMethodHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "fadeOutPlayers", returnFalseMethodHook);

            // 6. Complete suppression of focus loss dispatch in FocusRequester
            // This prevents ANY negative focus change (-1, -2, -3 ducking) from reaching client apps (e.g. YouTube)
            hookAllMethods(FOCUS_REQUESTER, "handleFocusLoss", noOpMethodHook);
            hookAllMethods(FOCUS_REQUESTER, "handleFocusLossFromGain", returnFalseMethodHook);
            hookAllMethods(FOCUS_REQUESTER, "dispatchFocusChange", returnZeroMethodHook);
            hookAllMethods(FOCUS_REQUESTER, "dispatchFocusChangeWithFadeLocked", returnZeroMethodHook);
            hookAllMethods(FOCUS_REQUESTER, "frameworkHandleFocusLoss", returnFalseMethodHook);

            // 7. Block ducking, call muting, and player attenuation in PlaybackActivityMonitor
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "duckPlayers", returnFalseMethodHook);
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "unduckPlayers", noOpMethodHook);
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "mutePlayersForCall", noOpMethodHook);

            // 8. Block DuckingManager & DuckedApp VolumeShaper ducking
            hookAllMethods(DUCKING_MANAGER, "duckUid", noOpMethodHook);
            hookAllMethods(DUCKING_MANAGER, "checkDuck", noOpMethodHook);
            hookAllMethods(DUCKED_APP, "addDuck", noOpMethodHook);

            // 9. Block FadeOutManager from fading out or lowering player volume
            hookAllMethods(FADE_OUT_MANAGER, "isFadeEnabled", returnFalseMethodHook);
            hookAllMethods(FADE_OUT_MANAGER, "canCauseFadeOut", returnFalseMethodHook);
            hookAllMethods(FADE_OUT_MANAGER, "canBeFadedOut", returnFalseMethodHook);
            hookAllMethods(FADE_OUT_MANAGER, "fadeOutUid", noOpMethodHook);
            hookAllMethods(FADE_OUT_MANAGER, "checkFade", noOpMethodHook);

            // 10. Xiaomi HyperOS / MIUI specific PlaybackActivityMonitor stubs
            hookAllMethods(PAM_STUB, "interruptMusicPlayback", returnFalseMethodHook);
            hookAllMethods(PAM_STUB, "ignoreFocusRequest", returnTrueMethodHook);
            hookAllMethods(PAM_STUB_IMPL, "interruptMusicPlayback", returnFalseMethodHook);
            hookAllMethods(PAM_STUB_IMPL, "ignoreFocusRequest", returnTrueMethodHook);
            hookAllMethods(PAM_STUB_IMPL, "updatePlayerVolumeByApc", noOpMethodHook);
            hookAllMethods(PAM_STUB_IMPL, "updatePlayerVolume", noOpMethodHook);

            // 11. Block call-related stream muting and volume suppression in AudioService
            hookAllMethods(AUDIO_SERVICE, "muteRingerModeStreams", noOpMethodHook);
            hookAllMethods(AUDIO_SERVICE, "setRingerModeInt", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    if (args != null && args.length > 0 && args[0] instanceof Integer) {
                        int ringerMode = (Integer) args[0];
                        if (ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                            param.setResult(null);
                        }
                    }
                }
            });

            Log.i(TAG, "Multi Audio Focus, focus loss suppression & call ducking elimination initialized successfully");
            XposedLog.d(TAG, "Multi Audio Focus, focus loss suppression & call ducking elimination initialized successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing DisableAudioFocus: " + t.getMessage(), t);
            XposedLog.e(TAG, getPackageName(), t);
        }
    }
}
