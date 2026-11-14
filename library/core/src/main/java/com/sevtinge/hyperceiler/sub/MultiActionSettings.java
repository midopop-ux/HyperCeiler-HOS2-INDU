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
package com.sevtinge.hyperceiler.sub;

import static com.sevtinge.hyperceiler.sub.SubPickerActivity.CALLBACK_MODE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.core.R;
import com.sevtinge.hyperceiler.dashboard.SettingsPreferenceFragment;

import java.util.HashMap;

import fan.preference.RadioButtonPreference;
import fan.preference.RadioButtonPreferenceCategory;

public class MultiActionSettings extends SettingsPreferenceFragment
    implements Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener,
    AppSelectorRadioButtonPreference.onArrowViewClickListener {

    Bundle args;
    String mKey = null;
    String mActionKey;

    private int mCurrentOptimizeMode;

    int[] mMultiActionValues;
    String[] mMultiActionKeys;

    HashMap<String , Integer> mMultiActions = new HashMap<>();

    private AppSelectorRadioButtonPreference mAppPreference;
    private AppSelectorRadioButtonPreference mActivityPreference;
    private AppSelectorRadioButtonPreference mTogglePreference;

    @Override
    public int getPreferenceScreenResId() {
        return R.xml.home_multi_action;
    }

    @Override
    public void initPrefs() {
        args = getArguments();
        mKey = args.getString("key");
        mActionKey = mKey + "_action";

        mCurrentOptimizeMode = getMultiActionMode();
        if (mCurrentOptimizeMode == 5) {
            mCurrentOptimizeMode = 4;
            setMultiActionMode(4);
        }
        mMultiActionValues = getResources().getIntArray(R.array.multi_action_value);
        mMultiActionKeys = getResources().getStringArray(R.array.multi_action_key);
        generateScreenColorPreference();
    }

    private int getMultiActionMode() {
        return hasKey(mActionKey) ? PrefsBridge.getInt(mActionKey, 0) : 0;
    }

    private void setMultiActionMode(int mode) {
        PrefsBridge.putByApp(mActionKey, mode);
    }

    public void updateAppSelectorTitle() {
        if (mAppPreference != null) {
            if (hasKey(mKey + "_app")) {
                String title = getAppName(getContext(), PrefsBridge.getString(mKey + "_app", ""));
                mAppPreference.setSummary(title != null ? title : getResources().getString(R.string.array_global_actions_launch_choose));
            } else {
                mAppPreference.setSummary(getResources().getString(R.string.array_global_actions_launch_choose));
            }
        }
    }

    public void updateActivityTitle() {
        if (mActivityPreference != null) {
            if (hasKey(mKey + "_activity")) {
                String val = PrefsBridge.getString(mKey + "_activity", "");
                String title = getAppName(getContext(), val, false);
                if (title != null && !title.isEmpty()) {
                    mActivityPreference.setSummary(title + " (" + val.replace("|", "/") + ")");
                } else if (!val.isEmpty()) {
                    mActivityPreference.setSummary(val.replace("|", "/"));
                } else {
                    mActivityPreference.setSummary(getResources().getString(R.string.array_global_actions_select_activity));
                }
            } else {
                mActivityPreference.setSummary(getResources().getString(R.string.array_global_actions_select_activity));
            }
        }
    }

    public void updateToggleTitle() {
        if (mTogglePreference != null) {
            String customTitle = hasKey(mKey + "_toggle_title") ? PrefsBridge.getString(mKey + "_toggle_title", "") : "";
            if (!customTitle.isEmpty()) {
                mTogglePreference.setSummary(customTitle);
                return;
            }
            int toggleVal = hasKey(mKey + "_toggle") ? PrefsBridge.getInt(mKey + "_toggle", 1) : 1;
            String[] toggleTitles = getResources().getStringArray(R.array.global_toggles);
            int[] toggleValues = getResources().getIntArray(R.array.global_toggles_val);
            String summary = getResources().getString(R.string.array_global_actions_select_toggle);
            for (int i = 0; i < toggleValues.length; i++) {
                if (toggleValues[i] == toggleVal && i < toggleTitles.length) {
                    summary = toggleTitles[i];
                    break;
                }
            }
            mTogglePreference.setSummary(summary);
        }
    }

    public static String getAppName(Context context, String pkgActName) {
        return getAppName(context, pkgActName, false);
    }

    public static String getAppName(Context context, String pkgActName, boolean forcePkg) {
        PackageManager pm = context.getPackageManager();
        String notSelected = "None";
        String[] pkgActArray = pkgActName.split("\\|");
        ApplicationInfo ai;

        if (!pkgActName.equals(notSelected)) {
            if (pkgActArray.length >= 1 && pkgActArray[0] != null) try {
                if (!forcePkg && pkgActArray.length >= 2 && pkgActArray[1] != null && !pkgActArray[1].trim().isEmpty() && !"null".equals(pkgActArray[1].trim())) {
                    return pm.getActivityInfo(new ComponentName(pkgActArray[0], pkgActArray[1]), 0).loadLabel(pm).toString();
                } else if (!pkgActArray[0].trim().isEmpty()) {
                    ai = pm.getApplicationInfo(pkgActArray[0], 0);
                    return pm.getApplicationLabel(ai).toString();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private final ActivityResultLauncher<Intent> appPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == 1 && result.getData() != null) {
                Intent data = result.getData();
                String mAppPackageName = data.getStringExtra("appPackageName");
                String mAppActivityName = data.getStringExtra("appActivityName");
                PrefsBridge.putByApp(mKey + "_app", mAppPackageName + "|" + mAppActivityName);
                updateAppSelectorTitle();
            }
        });

    private final ActivityResultLauncher<Intent> activityPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == 1 && result.getData() != null) {
                Intent data = result.getData();
                String mAppPackageName = data.getStringExtra("appPackageName");
                if (mAppPackageName != null && !mAppPackageName.isEmpty()) {
                    showActivitySelectionDialog(mAppPackageName);
                }
            }
        });

    private void generateScreenColorPreference() {
        PreferenceCategory preferenceCategory = generateCategory();
        String[] multiActionTitles = getResources().getStringArray(R.array.multi_action_title);
        for (int i = 0; i < mMultiActionKeys.length; i++) {
            String key = mMultiActionKeys[i];
            if (!shouldShowAction(key)) {
                continue;
            }
            mMultiActions.put(key, mMultiActionValues[i]);
            RadioButtonPreferenceCategory radioButtonPreferenceCategory = new RadioButtonPreferenceCategory(getThemedContext());
            preferenceCategory.addPreference(radioButtonPreferenceCategory);

            if (key.equals("prefs_key_open_app")) {
                mAppPreference = new AppSelectorRadioButtonPreference(getThemedContext());
                mAppPreference.setKey(key);
                mAppPreference.setTitle(multiActionTitles[i]);
                mAppPreference.setPersistent(false);
                mAppPreference.setLayoutResource(fan.preference.R.layout.miuix_preference_radiobutton_two_state_background);
                mAppPreference.setOnPreferenceChangeListener(this);
                mAppPreference.setOnPreferenceClickListener(this);
                mAppPreference.setArrowViewClickListener(this::pickApp);
                radioButtonPreferenceCategory.addPreference(mAppPreference);
                mAppPreference.setChecked(mMultiActionValues[i] == mCurrentOptimizeMode);
                updateAppSelectorTitle();
            } else if (key.equals("prefs_key_open_activity")) {
                mActivityPreference = new AppSelectorRadioButtonPreference(getThemedContext());
                mActivityPreference.setKey(key);
                mActivityPreference.setTitle(multiActionTitles[i]);
                mActivityPreference.setPersistent(false);
                mActivityPreference.setLayoutResource(fan.preference.R.layout.miuix_preference_radiobutton_two_state_background);
                mActivityPreference.setOnPreferenceChangeListener(this);
                mActivityPreference.setOnPreferenceClickListener(this);
                mActivityPreference.setArrowViewClickListener(this::pickActivity);
                radioButtonPreferenceCategory.addPreference(mActivityPreference);
                mActivityPreference.setChecked(mMultiActionValues[i] == mCurrentOptimizeMode);
                updateActivityTitle();
            } else if (key.equals("prefs_key_toggle_feature")) {
                mTogglePreference = new AppSelectorRadioButtonPreference(getThemedContext());
                mTogglePreference.setKey(key);
                mTogglePreference.setTitle(multiActionTitles[i]);
                mTogglePreference.setPersistent(false);
                mTogglePreference.setLayoutResource(fan.preference.R.layout.miuix_preference_radiobutton_two_state_background);
                mTogglePreference.setOnPreferenceChangeListener(this);
                mTogglePreference.setOnPreferenceClickListener(this);
                mTogglePreference.setArrowViewClickListener(this::pickToggle);
                radioButtonPreferenceCategory.addPreference(mTogglePreference);
                mTogglePreference.setChecked(mMultiActionValues[i] == mCurrentOptimizeMode);
                updateToggleTitle();
            } else {
                RadioButtonPreference radioButtonPreference = new RadioButtonPreference(getThemedContext());
                radioButtonPreference.setKey(mMultiActionKeys[i]);
                radioButtonPreference.setTitle(multiActionTitles[i]);
                radioButtonPreference.setPersistent(false);
                radioButtonPreference.setLayoutResource(fan.preference.R.layout.miuix_preference_radiobutton_two_state_background);
                radioButtonPreference.setOnPreferenceChangeListener(this);
                radioButtonPreference.setOnPreferenceClickListener(this);
                radioButtonPreferenceCategory.addPreference(radioButtonPreference);
                radioButtonPreference.setChecked(mMultiActionValues[i] == mCurrentOptimizeMode);
            }
        }
    }

    private boolean shouldShowAction(String key) {
        if ("prefs_key_go_home".equals(key)) {
            return supportsGoHomeAction();
        }
        if ("prefs_key_force_stop_current_app".equals(key)) {
            return supportsLineLongPressAction();
        }
        if ("prefs_key_google_voice_assistant".equals(key)) {
            return true;
        }
        if ("prefs_key_super_xiaoai_circle_search".equals(key)
            || "prefs_key_google_circle_to_search".equals(key)
            ) {
            return supportsAssistantGestureAction();
        }
        return true;
    }

    private boolean supportsGoHomeAction() {
        return "prefs_key_home_gesture_line_double_click".equals(mKey)
            || "prefs_key_home_gesture_line_long_press".equals(mKey)
            || "prefs_key_home_navigation_assist_left_slide".equals(mKey)
            || "prefs_key_home_navigation_assist_right_slide".equals(mKey);
    }

    private boolean supportsLineLongPressAction() {
        return "prefs_key_home_gesture_line_long_press".equals(mKey);
    }

    private boolean supportsAssistantGestureAction() {
        return "prefs_key_home_gesture_line_long_press".equals(mKey)
            || "prefs_key_home_gesture_line_double_click".equals(mKey)
            || "prefs_key_home_navigation_assist_left_slide".equals(mKey)
            || "prefs_key_home_navigation_assist_right_slide".equals(mKey);
    }

    private PreferenceCategory generateCategory() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        PreferenceCategory preferenceCategory = new PreferenceCategory(getPrefContext());
        preferenceCategory.setKey("multi_action");
        preferenceCategory.setPersistent(false);
        preferenceScreen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        String key = preference.getKey();
        if (preference instanceof RadioButtonPreference) {
            updateRadioButtonPreference(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String key = preference.getKey();
        if (preference instanceof RadioButtonPreference) {
            updateRadioButtonPreference(key);
            return true;
        }
        return false;
    }

    private void updateRadioButtonPreference(String key) {
        Integer mode = mMultiActions.get(key);
        if (mode != null) {
            setMultiActionMode(mode);
        }
        PreferenceCategory preferenceCategory = getPreferenceScreen().findPreference("multi_action");
        if (preferenceCategory != null) {
            int preferenceCount = preferenceCategory.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference cat = preferenceCategory.getPreference(i);
                if (cat instanceof RadioButtonPreferenceCategory category) {
                    if (category.getPreferenceCount() > 0) {
                        Preference p = category.getPreference(0);
                        if (p instanceof RadioButtonPreference pref) {
                            pref.setChecked(pref.getKey().equals(key));
                        }
                    }
                }
            }
        }
    }

    private void pickApp() {
        Intent intent = new Intent(getActivity(), SubPickerActivity.class);
        intent.putExtra("mode", CALLBACK_MODE);
        intent.putExtra("key", mKey);
        appPickerLauncher.launch(intent);
    }

    private void pickActivity() {
        Intent intent = new Intent(getActivity(), SubPickerActivity.class);
        intent.putExtra("mode", CALLBACK_MODE);
        intent.putExtra("key", mKey);
        activityPickerLauncher.launch(intent);
    }

    private void showActivitySelectionDialog(String packageName) {
        Context context = getContext();
        if (context == null) return;
        PackageManager pm = context.getPackageManager();
        try {
            android.content.pm.PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            if (pi.activities == null || pi.activities.length == 0) {
                fan.miuix.appcompat.widget.Toast.show(context, "No activities found in " + packageName);
                return;
            }
            java.util.List<String> activityNames = new java.util.ArrayList<>();
            java.util.List<String> displayNames = new java.util.ArrayList<>();
            for (android.content.pm.ActivityInfo ai : pi.activities) {
                if (ai.name == null) continue;
                activityNames.add(ai.name);
                CharSequence label = ai.loadLabel(pm);
                String labelStr = label != null ? label.toString() : "";
                String shortName = ai.name.startsWith(packageName) ? ai.name.substring(packageName.length()) : ai.name;
                if (!labelStr.isEmpty() && !labelStr.equals(ai.name)) {
                    displayNames.add(labelStr + " (" + shortName + ")");
                } else {
                    displayNames.add(shortName);
                }
            }
            CharSequence[] items = displayNames.toArray(new CharSequence[0]);
            new fan.appcompat.app.AlertDialog.Builder(getActivity())
                .setTitle(R.string.array_global_actions_select_activity)
                .setItems(items, (dialog, which) -> {
                    if (which >= 0 && which < activityNames.size()) {
                        String actName = activityNames.get(which);
                        PrefsBridge.putByApp(mKey + "_activity", packageName + "|" + actName);
                        updateActivityTitle();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        } catch (Throwable t) {
            fan.miuix.appcompat.widget.Toast.show(context, "Failed to load activities: " + t.getMessage());
        }
    }


    private void pickToggle() {
        android.content.Context context = getContext();
        if (context == null) return;
        PackageManager pm = context.getPackageManager();

        java.util.List<String> displayTitles = new java.util.ArrayList<>();
        java.util.List<String> specs = new java.util.ArrayList<>();
        java.util.List<Integer> builtInVals = new java.util.ArrayList<>();

        String[] toggleTitles = getResources().getStringArray(R.array.global_toggles);
        int[] toggleValues = getResources().getIntArray(R.array.global_toggles_val);

        for (int i = 0; i < toggleTitles.length && i < toggleValues.length; i++) {
            displayTitles.add(toggleTitles[i]);
            builtInVals.add(toggleValues[i]);
            specs.add(null);
        }

        try {
            Intent tileIntent = new Intent("android.service.quicksettings.action.QS_TILE");
            java.util.List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentServices(tileIntent, 0);
            if (resolveInfos != null) {
                for (android.content.pm.ResolveInfo ri : resolveInfos) {
                    if (ri.serviceInfo == null) continue;
                    String pkg = ri.serviceInfo.packageName;
                    String name = ri.serviceInfo.name;
                    CharSequence label = ri.serviceInfo.loadLabel(pm);
                    CharSequence appName = ri.serviceInfo.applicationInfo != null ? ri.serviceInfo.applicationInfo.loadLabel(pm) : pkg;
                    String title = (label != null ? label.toString() : name) + " (" + appName + ")";
                    String spec = "custom(" + pkg + "/" + name + ")";
                    displayTitles.add(title);
                    builtInVals.add(0);
                    specs.add(spec);
                }
            }
        } catch (Throwable ignored) {
        }

        String currentSpec = hasKey(mKey + "_toggle_spec") ? PrefsBridge.getString(mKey + "_toggle_spec", null) : null;
        int currentVal = hasKey(mKey + "_toggle") ? PrefsBridge.getInt(mKey + "_toggle", 1) : 1;

        int checkedItem = 0;
        if (currentSpec != null && !currentSpec.isEmpty()) {
            for (int i = 0; i < specs.size(); i++) {
                if (currentSpec.equals(specs.get(i))) {
                    checkedItem = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < builtInVals.size(); i++) {
                if (builtInVals.get(i) == currentVal) {
                    checkedItem = i;
                    break;
                }
            }
        }

        CharSequence[] items = displayTitles.toArray(new CharSequence[0]);
        new fan.appcompat.app.AlertDialog.Builder(getActivity())
            .setTitle(R.string.array_global_actions_select_toggle)
            .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                if (which >= 0 && which < items.length) {
                    String spec = specs.get(which);
                    if (spec != null) {
                        PrefsBridge.putByApp(mKey + "_toggle_spec", spec);
                        PrefsBridge.putByApp(mKey + "_toggle_title", items[which].toString());
                    } else {
                        PrefsBridge.removeByApp(mKey + "_toggle_spec");
                        PrefsBridge.putByApp(mKey + "_toggle_title", items[which].toString());
                        PrefsBridge.putByApp(mKey + "_toggle", builtInVals.get(which));
                    }
                    updateToggleTitle();
                }
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onClick() {
        pickApp();
    }
}
