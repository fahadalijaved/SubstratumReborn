/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.util.views;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import projekt.substratum.R;

/**
 * Drop-in for android.app.ProgressDialog, which is a platform dialog and so draws One UI's
 * dialog and spinner on a Galaxy. Same method names as the subset the app calls; shows an
 * M3 Expressive dialog (MaterialAlertDialogBuilder) with the morphing LoadingIndicator, or a
 * wavy LinearProgressIndicator for STYLE_HORIZONTAL (determinate) progress.
 */
public final class RebornProgressDialog {

    public static final int STYLE_SPINNER = 0;
    public static final int STYLE_HORIZONTAL = 1;

    private final Context context;
    private CharSequence title;
    private CharSequence message;
    private boolean cancelable = true;
    private boolean horizontal;
    private int max = 100;
    private int progress;
    private DialogInterface.OnCancelListener onCancel;
    private AlertDialog dialog;
    private TextView messageView;
    private LinearProgressIndicator bar;

    public RebornProgressDialog(Context context) {
        this.context = context;
    }

    /** The theme argument of ProgressDialog(Context, int) is ignored: the M3E dialog theme applies. */
    public RebornProgressDialog(Context context, int ignoredTheme) {
        this(context);
    }

    public void setTitle(@Nullable CharSequence title) {
        this.title = title;
        if (dialog != null) dialog.setTitle(title);
    }

    public void setMessage(@Nullable CharSequence message) {
        this.message = message;
        if (messageView != null) messageView.setText(message);
    }

    /** Kept for call compatibility: STYLE_HORIZONTAL is determinate, the spinner never is. */
    public void setIndeterminate(boolean indeterminate) {
    }

    public void setProgressStyle(int style) {
        horizontal = style == STYLE_HORIZONTAL;
    }

    public void setMax(int max) {
        this.max = max;
        if (bar != null) bar.setMax(max);
    }

    public void setProgress(int progress) {
        this.progress = progress;
        if (bar != null) bar.setProgressCompat(progress, true);
    }

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        if (dialog != null) dialog.setCancelable(cancelable);
    }

    public void setOnCancelListener(@Nullable DialogInterface.OnCancelListener listener) {
        onCancel = listener;
        if (dialog != null) dialog.setOnCancelListener(listener);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    @Nullable
    public Window getWindow() {
        if (dialog == null) dialog = build();
        return dialog.getWindow();
    }

    public void show() {
        if (dialog == null) dialog = build();
        dialog.show();
    }

    public void dismiss() {
        // Safe after the owning screen was recreated or closed (see SafeDialogs)
        SafeDialogs.dismiss(dialog);
    }

    public void cancel() {
        if (dialog != null) dialog.cancel();
    }

    private AlertDialog build() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        // Inflated in the dialog's own themed context, so the body takes the dialog's colours
        View view = LayoutInflater.from(builder.getContext())
                .inflate(R.layout.reborn_progress_dialog, null, false);
        messageView = view.findViewById(R.id.reborn_progress_message);
        bar = view.findViewById(R.id.reborn_progress_bar);
        messageView.setText(message);
        view.findViewById(R.id.reborn_progress_shape).setVisibility(horizontal ? View.GONE : View.VISIBLE);
        bar.setVisibility(horizontal ? View.VISIBLE : View.GONE);
        bar.setMax(max);
        bar.setProgressCompat(progress, false);
        AlertDialog built = builder
                .setTitle(title)
                .setView(view)
                .setCancelable(cancelable)
                .setOnCancelListener(onCancel)
                .create();
        built.setCanceledOnTouchOutside(false);
        return built;
    }
}
