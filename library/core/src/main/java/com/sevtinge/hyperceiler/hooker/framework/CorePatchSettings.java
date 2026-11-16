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
package com.sevtinge.hyperceiler.hooker.framework;

import android.content.Context;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.sevtinge.hyperceiler.core.R;
import com.sevtinge.hyperceiler.dashboard.DashboardFragment;

public class CorePatchSettings extends DashboardFragment {

    @Override
    public int getPreferenceScreenResId() {
        return R.xml.framework_core_patch;
    }

    @Override
    public void initPrefs() {
        setTitle("Core Patch");

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        screen.removeAll();

        Context context = getContext();
        if (context == null) {
            return;
        }

        String[][] options = new String[][]{
                {
                        "prefs_key_system_framework_core_patch_downgr",
                        "Bypass downgrade",
                        "Allow installing an older version over a newer version."
                },
                {
                        "prefs_key_system_framework_core_patch_auth_creak",
                        "Bypass verification",
                        "Allow installing APKs with invalid or modified signatures."
                },
                {
                        "prefs_key_system_framework_bypass_resource_arsc",
                        "Bypass resources.arsc alignment restriction",
                        "Allow installing APKs whose resources.arsc is compressed or not 4-byte aligned on Android 11 and later."
                },
                {
                        "prefs_key_system_framework_core_patch_digest_creak",
                        "Bypass digest",
                        "Allow reinstalling an app with a different signature."
                },
                {
                        "prefs_key_system_framework_core_patch_exact_signature_check",
                        "Bypass exact signature match",
                        "Allow split APKs to use different signatures."
                },
                {
                        "prefs_key_system_framework_core_patch_use_pre_signature",
                        "Use previous signatures (dangerous)",
                        "Always use the installed app's signatures during installation."
                },
                {
                        "prefs_key_system_framework_allow_hidden_apis",
                        "Allow hidden APIs for system apps",
                        "Allow system apps with signatures that do not match the platform signature to use hidden APIs without restriction."
                },
                {
                        "prefs_key_system_framework_core_patch_shared_user",
                        "Bypass shared user verification",
                        "Allow packages with a different shared-user signature."
                },
                {
                        "prefs_key_system_framework_disable_verification_agent",
                        "Disable Package Verification Agent",
                        "For example, Google Play Protect."
                },
                {
                        "prefs_key_system_framework_bypass_block",
                        "Bypass installation blocklist",
                        "Bypass blocklists on devices such as Nothing Phone."
                }
        };

        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);

        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            SwitchPreference pref = new SwitchPreference(context);
            pref.setKey(opt[0]);
            pref.setTitle(opt[1]);
            pref.setSummary(opt[2]);
            pref.setOrder(i);
            category.addPreference(pref);
        }

        Preference preSigPref = screen.findPreference("prefs_key_system_framework_core_patch_use_pre_signature");
        if (preSigPref != null) {
            preSigPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (Boolean.TRUE.equals(newValue)) {
                    new fan.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Use previous signatures (dangerous)")
                            .setMessage("Always use the installed app's signatures during installation.\n\n⚠️ Xiaomi/Redmi users must not enable this option; otherwise, the device may fail to boot.\nAny APK may overwrite the installed app. Enable only when necessary.")
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                ((SwitchPreference) preference).setChecked(true);
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                ((SwitchPreference) preference).setChecked(false);
                            })
                            .show();
                    return false;
                }
                return true;
            });
        }
    }
}
