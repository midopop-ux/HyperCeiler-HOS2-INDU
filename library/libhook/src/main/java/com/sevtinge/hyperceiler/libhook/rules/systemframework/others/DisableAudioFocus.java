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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

public class DisableAudioFocus extends BaseHook {

    private static final String TAG = "DisableAudioFocus";
    private static final String MEDIA_FOCUS_CONTROL = "com.android.server.audio.MediaFocusControl";
    private static final String AUDIO_SERVICE = "com.android.server.audio.AudioService";

    private static volatile int sSavedMediaVolume = -1;
    private static volatile boolean sInCallOrRing = false;

    @Override
    public void init() {
        Log.i(TAG, "Initializing DisableAudioFocus (Volume Monitor & Restore)");
        try {
            // 1. الأساسي النظيف: تفعيل ميزة Multi Audio Focus حتى لا توقف التطبيقات بعضها
            findAndHookMethodReplace(MEDIA_FOCUS_CONTROL, "getMultiAudioFocusEnabled", new IReplaceHook() {
                @Override
                public Object replace(HookParam param) {
                    return Boolean.TRUE;
                }
            });

            // 2. مراقبة حالة المكالمة/الرنين وتثبيت مستوى صوت الوسائط بدقة (منع النزول والارتفاع النسبي)
            hookAllMethods(AUDIO_SERVICE, "setModeInt", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object[] args = param.getArgs();
                        if (args == null || args.length == 0 || !(args[0] instanceof Integer)) {
                            return;
                        }
                        int mode = (Integer) args[0];
                        Object audioService = param.getThisObject();
                        Context context = (Context) getObjectField(audioService, "mContext");
                        if (context == null) return;

                        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                        if (am == null) return;

                        boolean isCallState = (mode == AudioManager.MODE_IN_CALL ||
                                mode == AudioManager.MODE_RINGTONE ||
                                mode == AudioManager.MODE_IN_COMMUNICATION ||
                                mode == AudioManager.MODE_CALL_SCREENING);

                        if (isCallState) {
                            if (!sInCallOrRing) {
                                // بدأت المكالمة أو الرنين: حفظ القيمة المستهدفة الحقيقية للوسائط
                                int currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                                if (currentVol > 0) {
                                    sSavedMediaVolume = currentVol;
                                    Log.i(TAG, "Call starting. Locked target STREAM_MUSIC volume: " + sSavedMediaVolume);
                                }
                                sInCallOrRing = true;
                            }

                            // أثناء المكالمة: إذا نزل الصوت، أعده فوراً إلى sSavedMediaVolume
                            final int targetVol = sSavedMediaVolume;
                            if (targetVol > 0) {
                                Handler mainHandler = new Handler(Looper.getMainLooper());
                                int[] delays = {0, 150, 400, 800, 1500};
                                for (int delay : delays) {
                                    mainHandler.postDelayed(() -> {
                                        try {
                                            int nowVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                                            if (nowVol < targetVol) {
                                                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
                                                Log.i(TAG, "Restored STREAM_MUSIC to target " + targetVol + " (was " + nowVol + ")");
                                            }
                                        } catch (Throwable t) {
                                            Log.w(TAG, "Error restoring volume: " + t.getMessage());
                                        }
                                    }, delay);
                                }
                            }
                        } else if (mode == AudioManager.MODE_NORMAL) {
                            // انتهت المكالمة: منع النظام من مضاعفة أو رفع الصوت فوق الدرجة الأصلية المحفوظة
                            final int targetVol = sSavedMediaVolume;
                            if (sInCallOrRing && targetVol > 0) {
                                Handler mainHandler = new Handler(Looper.getMainLooper());
                                int[] postDelays = {50, 200, 500, 1000};
                                for (int delay : postDelays) {
                                    mainHandler.postDelayed(() -> {
                                        try {
                                            int nowVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                                            if (nowVol > targetVol) {
                                                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
                                                Log.i(TAG, "Clamped post-call STREAM_MUSIC volume to original " + targetVol + " (was " + nowVol + ")");
                                            }
                                        } catch (Throwable t) {
                                            Log.w(TAG, "Error clamping post-call volume: " + t.getMessage());
                                        }
                                    }, delay);
                                }
                            }
                            sInCallOrRing = false;
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error in setModeInt hook: " + t.getMessage(), t);
                    }
                }
            });

            Log.i(TAG, "DisableAudioFocus cleanly initialized successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing DisableAudioFocus: " + t.getMessage(), t);
        }
    }
}
