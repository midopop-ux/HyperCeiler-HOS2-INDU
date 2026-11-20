/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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
package com.sevtinge.hyperceiler.hooker.securitycenter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.sevtinge.hyperceiler.core.R;
import com.sevtinge.hyperceiler.dashboard.DashboardFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import fan.preference.DropDownPreference;

public class ApplicationsSettings extends DashboardFragment {

    /**
     * Master switch preference key for Security Center uninstallation interception.
     */
    private static final String KEY_MASTER_SWITCH = "prefs_key_security_center_uninstall_with_default_package_manager";

    /**
     * Uninstaller selection preference key.
     */
    private static final String KEY_UNINSTALLER = "prefs_key_security_center_uninstaller";

    /**
     * Hook point timing selection key.
     */
    private static final String KEY_HOOK_POINT = "prefs_key_security_center_uninstall_hook_point";

    /**
     * Empty string: delegate resolution to system default resolver.
     */
    private static final String VALUE_FOLLOW_SYSTEM = "";

    /**
     * Follow system default installer token.
     */
    private static final String VALUE_FOLLOW_DEFAULT_INSTALLER = "__follow_default_installer__";

    /**
     * Probe package name to query activities handling package: uninstalls.
     */
    private static final String PROBE_PACKAGE = "com.android.shell";

    /**
     * APK MIME type used to query the default installer.
     */
    private static final String MIME_APK = "application/vnd.android.package-archive";

    @Override
    public int getPreferenceScreenResId() {
        return R.xml.security_center_app;
    }

    @Override
    public void initPrefs() {
        DropDownPreference uninstaller = findPreference(KEY_UNINSTALLER);
        DropDownPreference hookPoint = findPreference(KEY_HOOK_POINT);

        if (uninstaller != null) {
            populateUninstallerChoices(uninstaller);
            if (TextUtils.isEmpty(uninstaller.getValue())) {
                uninstaller.setValue(VALUE_FOLLOW_SYSTEM);
            }
        }

        applyDependentVisibility(uninstaller, hookPoint,
            getSharedPreferences().getBoolean(KEY_MASTER_SWITCH, false));

        Preference masterSwitch = findPreference(KEY_MASTER_SWITCH);
        if (masterSwitch != null) {
            masterSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                applyDependentVisibility(uninstaller, hookPoint, Boolean.TRUE.equals(newValue));
                return true;
            });
        }
    }

    /**
     * Update visibility of dependent preferences according to master switch state.
     */
    private void applyDependentVisibility(Preference uninstaller, Preference hookPoint, boolean visible) {
        if (uninstaller != null) {
            uninstaller.setVisible(visible);
        }
        if (hookPoint != null) {
            hookPoint.setVisible(visible);
        }
    }

    /**
     * Enumerate available uninstaller apps on the device.
     */
    private void populateUninstallerChoices(DropDownPreference uninstaller) {
        Context context = getContext();
        if (context == null) return;

        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", PROBE_PACKAGE, null));
        List<ResolveInfo> infos = pm.queryIntentActivities(probe, 0);

        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        Set<String> seenPackages = new LinkedHashSet<>();

        entries.add(context.getString(R.string.security_uninstaller_system_default));
        values.add(VALUE_FOLLOW_SYSTEM);

        // Follow default installer: display label here; actual resolution happens per uninstall.
        entries.add(buildFollowInstallerLabel(context, pm));
        values.add(VALUE_FOLLOW_DEFAULT_INSTALLER);

        if (infos != null) {
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null) continue;
                String pkgName = info.activityInfo.packageName;
                if (TextUtils.isEmpty(pkgName) || !seenPackages.add(pkgName)) continue;
                entries.add(info.loadLabel(pm) + " (" + pkgName + ")");
                values.add(pkgName);
            }
        }

        uninstaller.setEntries(entries.toArray(new CharSequence[0]));
        uninstaller.setEntryValues(values.toArray(new CharSequence[0]));
    }

    /**
     * Build label for 'Follow default installer (app name)'.
     */
    private CharSequence buildFollowInstallerLabel(Context context, PackageManager pm) {
        String pkgName = resolveDefaultInstallerPackage(context);
        if (TextUtils.isEmpty(pkgName)) {
            return context.getString(R.string.security_uninstaller_follow_default_installer_unknown);
        }

        CharSequence label = pkgName;
        try {
            label = pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0));
        } catch (Throwable ignored) {
            // Fall back to package name if label is unavailable.
        }
        return context.getString(R.string.security_uninstaller_follow_default_installer, label);
    }

    /**
     * Resolves package name of the default installer for APK files.
     */
    private String resolveDefaultInstallerPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("file:///sdcard/__probe__.apk"), MIME_APK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) return null;
        return info.activityInfo.packageName;
    }
}
