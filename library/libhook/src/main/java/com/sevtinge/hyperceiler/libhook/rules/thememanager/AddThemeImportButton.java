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
package com.sevtinge.hyperceiler.libhook.rules.thememanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Adds a fixed "Import / استيراد" bottom button in ThemeTabActivity.
 * Allows picking and importing third-party .mtz theme files directly into MIUI/HyperOS ThemeManager.
 */
public class AddThemeImportButton extends BaseHook {

    private static final int REQUEST_CODE_PICK_THEME = 0x4854; // "HT"
    private static Activity sCurrentActivity;

    @Override
    public void init() {
        Class<?> themeTabCls = findClassIfExists("com.android.thememanager.activity.ThemeTabActivity");
        if (themeTabCls == null) {
            themeTabCls = findClassIfExists("com.android.thememanager.ThemeResourceTabActivity");
        }
        if (themeTabCls == null) return;

        // 1. Hook onCreate to inject bottom button
        findAndHookMethod(themeTabCls, "onCreate", Bundle.class, new IMethodHook() {
            @Override
            public void after(HookParam param) {
                Activity activity = (Activity) param.getThisObject();
                new Handler(Looper.getMainLooper()).postDelayed(() -> addImportButton(activity), 300);
            }
        });

        // 2. Hook onActivityResult to handle chosen .mtz file
        findAndHookMethod(themeTabCls, "onActivityResult", int.class, int.class, Intent.class, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                int requestCode = (int) param.getArgs()[0];
                int resultCode = (int) param.getArgs()[1];
                Intent data = (Intent) param.getArgs()[2];

                if (requestCode == REQUEST_CODE_PICK_THEME && resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    Activity activity = (Activity) param.getThisObject();
                    if (uri != null && activity != null) {
                        handlePickedThemeUri(activity, uri);
                        param.setResult(null);
                    }
                }
            }
        });
    }

    private void addImportButton(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            if (decorView.findViewWithTag("hyperceiler_theme_import_btn") != null) return;

            sCurrentActivity = activity;
            Context ctx = activity;

            Button importBtn = new Button(ctx);
            importBtn.setTag("hyperceiler_theme_import_btn");
            importBtn.setText("Import (.mtz)");
            importBtn.setTextColor(Color.WHITE);
            importBtn.setTextSize(16);
            importBtn.setElevation(16f);

            // Modern rounded pill drawable with hyperOS blue gradient / color
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#007AFF"));
            bg.setCornerRadius(60f);
            importBtn.setBackground(bg);

            int btnHeight = (int) (48 * ctx.getResources().getDisplayMetrics().density);
            int marginHoriz = (int) (32 * ctx.getResources().getDisplayMetrics().density);
            int marginBottom = (int) (24 * ctx.getResources().getDisplayMetrics().density);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    btnHeight
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.leftMargin = marginHoriz;
            lp.rightMargin = marginHoriz;
            lp.bottomMargin = marginBottom;

            importBtn.setLayoutParams(lp);

            importBtn.setOnClickListener(v -> openFilePicker(activity));

            decorView.addView(importBtn);
            XposedLog.i(TAG, getPackageName(), "Successfully added import button to ThemeTabActivity");
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "Failed to add import button", t);
        }
    }

    private void openFilePicker(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            activity.startActivityForResult(Intent.createChooser(intent, "Select .mtz Theme"), REQUEST_CODE_PICK_THEME);
        } catch (Throwable t) {
            Toast.makeText(activity, "Failed to open file picker: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePickedThemeUri(Activity activity, Uri uri) {
        new Thread(() -> {
            try {
                // Ensure import dir exists: /storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/theme/.data/import/theme/
                File extFiles = activity.getExternalFilesDir(null);
                File themeImportDir = new File(extFiles, "MIUI/theme/.data/import/theme");
                if (!themeImportDir.exists()) {
                    themeImportDir.mkdirs();
                }

                String fileName = "imported_" + System.currentTimeMillis() + ".mtz";
                File destFile = new File(themeImportDir, fileName);

                try (InputStream in = activity.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(activity, "Theme imported: " + destFile.getName() + "\nRestarting view...", Toast.LENGTH_LONG).show();
                    // Refresh or trigger native import handler if present
                    triggerNativeImport(activity, destFile);
                    activity.recreate();
                });
            } catch (Throwable t) {
                XposedLog.w(TAG, getPackageName(), "handlePickedThemeUri failed", t);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(activity, "Import failed: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void triggerNativeImport(Activity activity, File mtzFile) {
        try {
            Class<?> handlerCls = findClassIfExists("com.android.thememanager.basemodule.unzip.ThemeImportHandler");
            if (handlerCls != null) {
                for (Method m : handlerCls.getDeclaredMethods()) {
                    if (m.getParameterCount() >= 1 && m.getParameterTypes()[0] == File.class) {
                        m.setAccessible(true);
                        m.invoke(null, mtzFile);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
