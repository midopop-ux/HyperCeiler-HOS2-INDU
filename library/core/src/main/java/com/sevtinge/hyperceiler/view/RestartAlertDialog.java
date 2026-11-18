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
package com.sevtinge.hyperceiler.view;

import static com.sevtinge.hyperceiler.common.utils.ShellUtils.checkRootPermission;

import android.content.Context;

import com.sevtinge.hyperceiler.core.R;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.AppsTool;
import com.sevtinge.hyperceiler.utils.DialogHelper;

import java.util.Arrays;
import java.util.List;

import fan.appcompat.app.AlertDialog;

public class RestartAlertDialog extends AlertDialog {

    private List<String> mHookedNames;
    private List<String> mHookedPkgs;
    private List<String> mAllNames;
    private List<String> mAllPkgs;
    private List<String> mCurrentPkgs;
    private MultipleChoiceView mChoiceView;

    public RestartAlertDialog(Context context) {
        this(context, null, null, null, null);
    }

    public RestartAlertDialog(Context context, List<String> appNameList, List<String> appPackageNameList) {
        this(context, appNameList, appPackageNameList, null, null);
    }

    public RestartAlertDialog(Context context,
                              List<String> hookedNames, List<String> hookedPkgs,
                              List<String> allNames, List<String> allPkgs) {
        super(context);
        setTitle(R.string.hyperceiler_restart_quick);

        mHookedNames = (hookedNames != null && !hookedNames.isEmpty()) ? hookedNames
                : Arrays.asList(context.getResources().getStringArray(R.array.restart_apps_name_hyperos));
        mHookedPkgs = (hookedPkgs != null && !hookedPkgs.isEmpty()) ? hookedPkgs
                : Arrays.asList(context.getResources().getStringArray(R.array.restart_apps_packagename));
        mAllNames = allNames;
        mAllPkgs = allPkgs;
        mCurrentPkgs = mHookedNames;

        mChoiceView = createMultipleChoiceView(context);
        if (mAllNames != null && !mAllNames.isEmpty()) {
            mChoiceView.setupToggleHeader(true, isChecked -> {
                if (isChecked) {
                    mCurrentPkgs = mAllPkgs;
                    mChoiceView.setData(mAllNames, null);
                } else {
                    mCurrentPkgs = mHookedNames;
                    mChoiceView.setData(mHookedNames, null);
                }
                mChoiceView.deselectAll();
            });
        }
        setView(mChoiceView);
    }

    private MultipleChoiceView createMultipleChoiceView(Context context) {
        MultipleChoiceView view = new MultipleChoiceView(context);
        view.setData(mHookedNames, null);
        view.deselectAll();
        view.setOnCheckedListener(sparseBooleanArray -> {
            dismiss();
            int size = sparseBooleanArray.size();
            if (checkRootPermission() != 0) {
                DialogHelper.showAlertDialog(context, false, true);
                return;
            }
            List<String> pkgsToKill = mCurrentPkgs != null ? mCurrentPkgs : mHookedPkgs;
            if (pkgsToKill != null) {
                for (int i = 0; i < size; i++) {
                    if (sparseBooleanArray.get(i) && i < pkgsToKill.size()) {
                        String packageGet = pkgsToKill.get(i);
                        AppsTool.killApps(packageGet);
                        if ("com.miui.home".equals(packageGet)) {
                            AppsTool.killApps("com.mi.android.globallauncher");
                        }
                    }
                }
            }
        });
        return view;
    }
}
