/*
 * This file is part of HyperCeiler.
 */

package com.sevtinge.hyperceiler.hooker;

import com.sevtinge.hyperceiler.core.R;
import com.sevtinge.hyperceiler.dashboard.DashboardFragment;

public class PocoLauncherFragment extends DashboardFragment {

    @Override
    public int getPreferenceScreenResId() {
        return R.xml.poco_launcher;
    }

    @Override
    public void initPrefs() {
    }
}
