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
package com.sevtinge.hyperceiler.libhook.rules.systemsettings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class FixPersonalizationIntent extends BaseHook {

    private static final String THEME_PACKAGE = "com.android.thememanager";
    private static final String PERSONALIZE_ACTIVITY = "com.android.thememanager.settings.personalize.activity.PersonalizeActivity";

    @Override
    public void init() {
        // 1. Hook handlePreferenceTreeClick on TopLevelWallpaperPreferenceController
        try {
            findAndHookMethod("com.android.settings.display.TopLevelWallpaperPreferenceController",
                    "handlePreferenceTreeClick",
                    Preference.class,
                    new IMethodHook() {
                        @Override
                        public void before(HookParam param) {
                            Preference preference = (Preference) param.getArgs()[0];
                            if (preference == null) return;

                            String key = preference.getKey();
                            if ("top_level_wallpaper".equals(key)) {
                                Context context = preference.getContext();
                                if (context == null) return;
                                if (launchChinaPersonalize(context)) {
                                    param.setResult(true);
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "FixPersonalizationIntent controller hook failed", t);
        }

        // 2. Direct hook on Activity.startActivity in Settings to catch WallpaperSettingsActivity
        try {
            findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Intent intent = (Intent) param.getArgs()[0];
                    if (intent == null) return;

                    ComponentName cn = intent.getComponent();
                    if (cn != null && THEME_PACKAGE.equals(cn.getPackageName()) &&
                            "com.android.thememanager.settings.WallpaperSettingsActivity".equals(cn.getClassName())) {
                        Activity activity = (Activity) param.getThisObject();
                        if (launchChinaPersonalize(activity)) {
                            param.setResult(null);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "FixPersonalizationIntent startActivity hook failed", t);
        }
    }

    private boolean launchChinaPersonalize(Context context) {
        try {
            PackageManager pm = context.getPackageManager();

            // Try explicit Component first
            Intent explicitIntent = new Intent();
            explicitIntent.setComponent(new ComponentName(THEME_PACKAGE, PERSONALIZE_ACTIVITY));
            explicitIntent.putExtra("app_launched_settings", "app_launched_settings");
            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (pm.resolveActivity(explicitIntent, 0) != null) {
                context.startActivity(explicitIntent);
                return true;
            }

            // Fallback to deep link: theme://zhuti.xiaomi.com/personalize
            Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("theme://zhuti.xiaomi.com/personalize"));
            deepLinkIntent.setPackage(THEME_PACKAGE);
            deepLinkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (pm.resolveActivity(deepLinkIntent, 0) != null) {
                context.startActivity(deepLinkIntent);
                return true;
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "launchChinaPersonalize failed", t);
        }
        return false;
    }
}
