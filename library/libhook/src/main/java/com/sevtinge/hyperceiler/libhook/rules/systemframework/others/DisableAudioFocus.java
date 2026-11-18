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

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class DisableAudioFocus extends BaseHook {

    private static final String TAG = "DisableAudioFocus";
    private static final String MEDIA_FOCUS_CONTROL = "com.android.server.audio.MediaFocusControl";

    @Override
    public void init() {
        try {
            IReplaceHook returnTrueHook = new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    return Boolean.TRUE;
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
                    param.setResult(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
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

            // 4. Force getMultiAudioFocusEnabled() to return true
            findAndHookMethodReplace(MEDIA_FOCUS_CONTROL, "getMultiAudioFocusEnabled", returnTrueHook);

            // 5. Block sending focus loss to existing players when another app starts playing
            IMethodHook suppressFocusLossHook = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            };
            hookAllMethods(MEDIA_FOCUS_CONTROL, "propagateFocusLossFromGain_syncAf", suppressFocusLossHook);
            hookAllMethods(MEDIA_FOCUS_CONTROL, "propagateFocusLossFromGain_syncAf_mi", suppressFocusLossHook);

            // 6. Block ducking (volume lowering) and fade out in MediaFocusControl
            hookAllMethods(MEDIA_FOCUS_CONTROL, "duckPlayers", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(Boolean.FALSE);
                }
            });
            hookAllMethods(MEDIA_FOCUS_CONTROL, "fadeOutPlayers", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(Boolean.FALSE);
                }
            });

            // 7. Block ducking, call muting, and player attenuation in PlaybackActivityMonitor & DuckingManager
            String PLAYBACK_ACTIVITY_MONITOR = "com.android.server.audio.PlaybackActivityMonitor";
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "duckPlayers", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(Boolean.FALSE);
                }
            });
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "unduckPlayers", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
            hookAllMethods(PLAYBACK_ACTIVITY_MONITOR, "mutePlayersForCall", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });

            String DUCKING_MANAGER = "com.android.server.audio.PlaybackActivityMonitor$DuckingManager";
            hookAllMethods(DUCKING_MANAGER, "duckUid", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });

            XposedLog.d(TAG, "Multi Audio Focus, requestAudioFocus bypass & ducking suppression initialized successfully");
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(), t);
        }
    }
}
