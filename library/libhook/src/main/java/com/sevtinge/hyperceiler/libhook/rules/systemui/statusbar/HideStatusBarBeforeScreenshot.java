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
package com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.lingqiqi5211.ezhooktool.core.java.Methods;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Hide status bar during screenshot taking.
 *
 * SystemUI notifies the screenshot pipeline via miui.intent.TAKE_SCREENSHOT.
 * IsFinished flag differentiates between start and finish.
 * State is managed per view and globally within the process to ensure idempotent restoration.
 */
public class HideStatusBarBeforeScreenshot extends BaseHook {

    private static final String LEGACY_COLLAPSED_STATUS_BAR_CLASS =
        "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment";
    private static final String OS4_STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    private static final String ACTION_TAKE_SCREENSHOT = "miui.intent.TAKE_SCREENSHOT";
    private static final String EXTRA_IS_FINISHED = "IsFinished";
    private static final String HOT_RELOAD_VIEW_KEY =
        "HideStatusBarBeforeScreenshot.statusBarView";

    private static final Object STATE_LOCK = new Object();
    /** Status bar views hidden by this rule and their restore info. */
    private static final Map<View, HiddenStatusBar> HIDDEN_STATUS_BARS = new WeakHashMap<>();
    /** Views that already registered a receiver to avoid duplicate registrations. */
    private static final Set<View> REGISTERED_VIEWS =
        Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static volatile boolean sLastSurfaceApplySynchronous;
    private static volatile long sHideStartedAt;

    /** Single hidden status bar view and its restoration state. */
    private static final class HiddenStatusBar {
        /** View visibility before hiding; restored on completion. */
        private final int visibilityBefore;
        /** Window SurfaceControl set to 0 alpha; null if not available. */
        @Nullable
        private final SurfaceControl surface;

        HiddenStatusBar(int visibilityBefore, @Nullable SurfaceControl surface) {
            this.visibilityBefore = visibilityBefore;
            this.surface = surface;
        }
    }

    @Override
    public void init() {
        // Reuse view registered in previous hot reload generation if present.
        View restoredView = getHotReloadRuntimeState(HOT_RELOAD_VIEW_KEY, View.class);
        // Ensure any hidden status bar is released before hot reload.
        registerHotReloadCleanup(this::releaseStatusBarForHotReload);
        if (restoredView != null) {
            registerScreenshotReceiver(restoredView);
        }

        if (Build.VERSION.SDK_INT >= 37) {
            XposedLog.d(TAG, getPackageName(),
                "HOOK_STATE=SELECTED target=" + OS4_STATUS_BAR_VIEW_CLASS
                    + "#onAttachedToWindow sdk=" + Build.VERSION.SDK_INT);
            hookAllMethods(OS4_STATUS_BAR_VIEW_CLASS, "onAttachedToWindow", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object thisObject = param.getThisObject();
                    if (thisObject instanceof View view) {
                        registerScreenshotReceiver(view);
                    }
                }
            });
        } else {
            XposedLog.d(TAG, getPackageName(),
                "HOOK_STATE=SELECTED target=" + LEGACY_COLLAPSED_STATUS_BAR_CLASS
                    + "#onViewCreated sdk=" + Build.VERSION.SDK_INT);
            hookAllMethods(LEGACY_COLLAPSED_STATUS_BAR_CLASS, "onViewCreated", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    View view = (View) param.getArgs()[0];
                    registerScreenshotReceiver(view);
                }
            });
        }
    }

    private void registerScreenshotReceiver(View view) {
        if (view == null) return;
        Context context = view.getContext();
        if (context == null) return;
        synchronized (STATE_LOCK) {
            if (!REGISTERED_VIEWS.add(view)) {
                XposedLog.d(TAG, getPackageName(),
                    "HOOK_STATE=SKIP_DUPLICATE_VIEW view=" + view.getClass().getName());
                return;
            }
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!ACTION_TAKE_SCREENSHOT.equals(intent.getAction())) return;

                boolean finished = intent.getBooleanExtra(EXTRA_IS_FINISHED, true);
                if (finished) {
                    restoreHiddenStatusBars();
                } else {
                    hideStatusBar(view);
                }

                XposedLog.d(TAG, getPackageName(),
                    "HOOK_STATE=CALLBACK_HIT finished=" + finished
                        + " view=" + view.getClass().getName()
                        + " visibility=" + view.getVisibility()
                        + " hiddenViews=" + countHiddenStatusBars()
                        + " surfaceSync=" + sLastSurfaceApplySynchronous
                        + " elapsedMs=" + (finished && sHideStartedAt > 0
                            ? SystemClock.uptimeMillis() - sHideStartedAt : 0));
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_TAKE_SCREENSHOT);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        putHotReloadRuntimeState(HOT_RELOAD_VIEW_KEY, view);
        XposedLog.d(TAG, getPackageName(),
            "HOOK_STATE=INSTALLED view=" + view.getClass().getName());
        registerReceiverHotReloadCleanup(context, receiver);
    }

    /**
     * Hide status bar view and record original visibility.
     */
    private void hideStatusBar(View view) {
        synchronized (STATE_LOCK) {
            if (HIDDEN_STATUS_BARS.containsKey(view)) {
                view.setVisibility(View.INVISIBLE);
                return;
            }
            if (HIDDEN_STATUS_BARS.isEmpty()) {
                sHideStartedAt = SystemClock.uptimeMillis();
            }
            int visibilityBefore = view.getVisibility();
            SurfaceControl surface = findStatusBarSurface(view);
            if (!applySurfaceAlpha(surface, 0.0f)) {
                surface = null;
            }
            HIDDEN_STATUS_BARS.put(view, new HiddenStatusBar(visibilityBefore, surface));
            view.setVisibility(View.INVISIBLE);
            XposedLog.d(TAG, getPackageName(),
                "HOOK_STATE=HIDE view=" + view.getClass().getName()
                    + " visibilityBefore=" + visibilityBefore
                    + " surface=" + (surface != null));
        }
    }

    /**
     * Restore all status bar views hidden by this rule.
     * Idempotent: Clears recorded map so subsequent calls do not re-hide.
     */
    private void restoreHiddenStatusBars() {
        List<Map.Entry<View, HiddenStatusBar>> entries;
        synchronized (STATE_LOCK) {
            if (HIDDEN_STATUS_BARS.isEmpty()) return;
            entries = new ArrayList<>(HIDDEN_STATUS_BARS.entrySet());
            HIDDEN_STATUS_BARS.clear();
            sHideStartedAt = 0L;
        }
        for (Map.Entry<View, HiddenStatusBar> entry : entries) {
            View view = entry.getKey();
            HiddenStatusBar hidden = entry.getValue();
            view.setVisibility(hidden.visibilityBefore);
            if (hidden.surface != null) {
                // If SurfaceControl was invalidated, re-resolve from current view
                SurfaceControl surface = hidden.surface.isValid()
                    ? hidden.surface
                    : findStatusBarSurface(view);
                applySurfaceAlpha(surface, 1.0f);
            }
            XposedLog.d(TAG, getPackageName(),
                "HOOK_STATE=RESTORE view=" + view.getClass().getName()
                    + " visibility=" + hidden.visibilityBefore);
        }
    }

    private int countHiddenStatusBars() {
        synchronized (STATE_LOCK) {
            return HIDDEN_STATUS_BARS.size();
        }
    }

    private void releaseStatusBarForHotReload() {
        restoreHiddenStatusBars();
        synchronized (STATE_LOCK) {
            REGISTERED_VIEWS.clear();
        }
    }

    @Nullable
    private SurfaceControl findStatusBarSurface(View view) {
        try {
            Object viewRoot = Methods.callMethod(view, "getViewRootImpl");
            Object surfaceObject = Methods.callMethod(viewRoot, "getSurfaceControl");
            if (surfaceObject instanceof SurfaceControl surface && surface.isValid()) {
                return surface;
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(), "HOOK_STATE=SURFACE_LOOKUP_FAILED", t);
        }
        return null;
    }

    private boolean applySurfaceAlpha(@Nullable SurfaceControl surface, float alpha) {
        if (surface == null || !surface.isValid()) {
            sLastSurfaceApplySynchronous = false;
            return false;
        }
        try {
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            try {
                transaction.setAlpha(surface, alpha);
                sLastSurfaceApplySynchronous = applySurfaceTransaction(transaction);
            } finally {
                transaction.close();
            }
            return true;
        } catch (Throwable t) {
            sLastSurfaceApplySynchronous = false;
            XposedLog.w(TAG, getPackageName(),
                "HOOK_STATE=SURFACE_APPLY_FAILED alpha=" + alpha, t);
            return false;
        }
    }

    private boolean applySurfaceTransaction(SurfaceControl.Transaction transaction) {
        try {
            @SuppressLint("SoonBlockedPrivateApi") Method applySynchronously = SurfaceControl.Transaction.class
                .getDeclaredMethod("apply", boolean.class);
            applySynchronously.setAccessible(true);
            applySynchronously.invoke(transaction, true);
            return true;
        } catch (Throwable ignored) {
            transaction.apply();
            return false;
        }
    }
}
