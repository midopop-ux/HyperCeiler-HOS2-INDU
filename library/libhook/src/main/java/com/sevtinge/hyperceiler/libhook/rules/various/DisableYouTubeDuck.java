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

package com.sevtinge.hyperceiler.libhook.rules.various;

import android.media.AudioManager;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class DisableYouTubeDuck extends BaseHook {

    private static final String TAG = "DisableYouTubeDuck";

    @Override
    public void init() {
        XposedLog.d(TAG, "Initializing DisableYouTubeDuck for " + getPackageName());
        try {
            // 1. Intercept client-side AudioFocus dispatcher in YouTube's process
            // When system server delivers an audio focus loss or ducking (-3 CAN_DUCK, -2 TRANSIENT, -1 LOSS),
            // suppress it so YouTube (ExoPlayer) never lowers its playback volume for calls or notifications.
            hookAllMethods("android.media.AudioManager", "dispatchAudioFocusChange", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object[] args = param.getArgs();
                        if (args != null && args.length > 0 && args[0] instanceof Integer) {
                            int focusChange = (Integer) args[0];
                            if (focusChange < 0) {
                                XposedLog.d(TAG, "Suppressed dispatchAudioFocusChange(" + focusChange + ") for YouTube");
                                param.setResult(null);
                            }
                        }
                    } catch (Throwable t) {
                        XposedLog.e(TAG, "Error in dispatchAudioFocusChange hook: " + t.getMessage());
                    }
                }
            });

            // 2. Block VolumeShaper ducking on AudioTrack inside YouTube
            Class<?> audioTrackClass = findClassIfExists("android.media.AudioTrack");
            if (audioTrackClass != null) {
                hookAllMethods(audioTrackClass, "applyVolumeShaper", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        XposedLog.d(TAG, "Intercepted AudioTrack.applyVolumeShaper to prevent ducking");
                        param.setResult(null);
                    }
                });
            }

            // 3. Block PlayerBase baseApplyVolumeShaper ducking inside YouTube
            Class<?> playerBaseClass = findClassIfExists("android.media.PlayerBase");
            if (playerBaseClass != null) {
                hookAllMethods(playerBaseClass, "baseApplyVolumeShaper", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        XposedLog.d(TAG, "Intercepted PlayerBase.baseApplyVolumeShaper to prevent ducking");
                        param.setResult(0);
                    }
                });

                // 4. Protect player volume from ducking attenuation (keep full volume)
                hookAllMethods(playerBaseClass, "baseSetVolume", new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        try {
                            Object[] args = param.getArgs();
                            if (args != null && args.length >= 2 && args[0] instanceof Float && args[1] instanceof Float) {
                                float left = (Float) args[0];
                                float right = (Float) args[1];
                                if (left > 0.0f && left < 0.9f) {
                                    args[0] = 1.0f;
                                }
                                if (right > 0.0f && right < 0.9f) {
                                    args[1] = 1.0f;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }

            XposedLog.d(TAG, "DisableYouTubeDuck successfully active for " + getPackageName());
        } catch (Throwable t) {
            XposedLog.e(TAG, "Failed to initialize DisableYouTubeDuck: " + t.getMessage(), t);
        }
    }
}
