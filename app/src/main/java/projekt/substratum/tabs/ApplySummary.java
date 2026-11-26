/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.tabs;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import projekt.substratum.R;
import projekt.substratum.Substratum;
import projekt.substratum.common.platform.SystemAmoled;
import projekt.substratum.common.platform.XposedBackend;

import static projekt.substratum.common.Resources.SYSTEMUI;

/**
 * One-tap 'Apply selected' on the LSPosed engine: enables what was just built, restarts the
 * targets once (including rebuilt overlays that were enabled already, which the two-step flow
 * left on their old version), then sums it up.
 */
final class ApplySummary {

    private static final String FRAMEWORK = "android";

    int built;
    int enabled;
    final List<String> failed = new ArrayList<>();
    boolean systemUiRestarted;
    boolean framework;
    final Set<String> closed = new LinkedHashSet<>();
    final Set<String> restartNeeded = new LinkedHashSet<>();
    CharSequence errorLog = "";
    // Overlays switched off for these ('Disable other themes', auto-disable)
    int turnedOff;

    /**
     * Enable the overlays that compiled and restart each affected target once. Background thread.
     *
     * @param built         Names of the overlays that compiled and were stored
     * @param disabledFirst Overlays already turned off for these (mix and match), not restarted
     * @param failed        Labels of the overlays that did not compile
     */
    static ApplySummary apply(Context context, List<String> built, List<String> disabledFirst,
                              List<String> failed, CharSequence errorLog) {
        ApplySummary summary = new ApplySummary();
        summary.failed.addAll(failed);
        summary.errorLog = errorLog == null ? "" : errorLog;
        List<String> stored = new ArrayList<>();
        for (String name : built) {
            if (XposedBackend.isStored(name)) {
                stored.add(name);
            } else {
                summary.failed.add(name);
            }
        }
        summary.built = stored.size();

        // As ThemeManager.enableOverlay does on this engine: optionally turn off the other
        // overlays of the same targets
        List<String> turnedOff = new ArrayList<>(disabledFirst);
        if (Substratum.getPreferences().getBoolean("auto_disable_target_overlays", false)) {
            Set<String> targets = new HashSet<>();
            for (String name : stored) {
                String target = XposedBackend.getTarget(name);
                if (target != null) targets.add(target);
            }
            List<String> others = new ArrayList<>();
            for (String other : XposedBackend.listAll()) {
                if (stored.contains(other) || !XposedBackend.isEnabled(other)) continue;
                if (targets.contains(XposedBackend.getTarget(other))) others.add(other);
            }
            XposedBackend.disable(others);
            turnedOff.addAll(others);
        }

        // Also switches the fabricated values back on: a rebuild registers them again
        XposedBackend.enable(stored);
        // One restart per target, covering rebuilt overlays that were on already (their targets
        // still hold the old build) and whatever was turned off for them
        List<String> restart = new ArrayList<>(stored);
        restart.addAll(turnedOff);
        if (!restart.isEmpty()) XposedBackend.restartTargets(context, restart);
        for (String name : new HashSet<>(turnedOff)) {
            if (!stored.contains(name) && !XposedBackend.isEnabled(name)) summary.turnedOff++;
        }

        // What happened to each target (mirrors XposedBackend.restartTargets)
        PackageManager pm = context.getPackageManager();
        for (String name : built) {
            if (!XposedBackend.isEnabled(name)) continue;
            summary.enabled++;
            String target = XposedBackend.getTarget(name);
            if (target == null) continue;
            if (SYSTEMUI.equals(target)) {
                summary.systemUiRestarted = true;
            } else if (FRAMEWORK.equals(target)) {
                summary.systemUiRestarted = true;
                summary.framework = true;
            } else if (SystemAmoled.safeToRestart(context, target)) {
                summary.closed.add(label(pm, target));
            } else {
                summary.restartNeeded.add(label(pm, target));
            }
        }
        return summary;
    }

    /**
     * Nothing was enabled (every overlay failed to build).
     */
    static ApplySummary failedOnly(List<String> failed, CharSequence errorLog) {
        ApplySummary summary = new ApplySummary();
        summary.failed.addAll(failed);
        summary.errorLog = errorLog == null ? "" : errorLog;
        return summary;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * @param onShowLog Opens the error log; only offered when there is one
     */
    void show(Activity activity, Runnable onShowLog) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View view = View.inflate(activity, R.layout.apply_summary_dialog, null);
        ((TextView) view.findViewById(R.id.summary_built)).setText(String.valueOf(built));
        ((TextView) view.findViewById(R.id.summary_enabled)).setText(String.valueOf(enabled));
        TextView failedCount = view.findViewById(R.id.summary_failed);
        failedCount.setText(String.valueOf(failed.size()));
        if (!failed.isEmpty()) {
            failedCount.setTextColor(resolveColor(activity,
                    R.attr.colorError));
            TextView names = view.findViewById(R.id.summary_failed_names);
            names.setText(activity.getString(R.string.apply_summary_failed_names,
                    String.join(", ", failed)));
            names.setVisibility(View.VISIBLE);
        }

        List<String> notes = new ArrayList<>();
        if (systemUiRestarted) notes.add(activity.getString(R.string.apply_summary_systemui));
        if (!closed.isEmpty()) {
            notes.add(activity.getString(R.string.apply_summary_closed,
                    String.join(", ", closed)));
        }
        if (!restartNeeded.isEmpty()) {
            notes.add(activity.getString(R.string.apply_summary_restart_needed,
                    String.join(", ", restartNeeded)));
        }
        if (framework) notes.add(activity.getString(R.string.apply_summary_framework));
        if (turnedOff > 0) {
            notes.add(activity.getResources().getQuantityString(
                    R.plurals.apply_summary_turned_off, turnedOff, turnedOff));
        }
        if (enabled < built) notes.add(activity.getString(R.string.apply_summary_not_enabled));
        if (!notes.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (String note : notes) {
                if (text.length() > 0) text.append('\n');
                text.append("•  ").append(note);
            }
            TextView notesView = view.findViewById(R.id.summary_notes);
            notesView.setText(text);
            notesView.setVisibility(View.VISIBLE);
        }

        int title;
        if (enabled == 0) {
            title = R.string.apply_summary_title_none;
        } else if (!failed.isEmpty()) {
            title = R.string.apply_summary_title_partial;
        } else {
            title = R.string.apply_summary_title;
        }
        android.graphics.drawable.Drawable icon = activity.getDrawable(
                enabled == 0 ? R.drawable.ic_reborn_error : R.drawable.ic_reborn_check);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(resolveColor(activity, enabled == 0 ?
                    R.attr.colorError :
                    R.attr.colorPrimary));
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setIcon(icon)
                .setView(view)
                .setPositiveButton(R.string.apply_summary_done, null);
        if (onShowLog != null && errorLog.length() > 0) {
            builder.setNeutralButton(R.string.apply_summary_log, (d, w) -> onShowLog.run());
        }
        builder.show();
    }

    private static int resolveColor(Context context, int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }
}
