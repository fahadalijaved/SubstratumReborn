/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.views;

import android.app.Dialog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The classic apply's dialogs, when applying recreated the screen they belonged to.
 */
public class SafeDialogsTest {

    /**
     * A dialog whose window went with its activity: dismissing it throws, as Android's
     * WindowManagerGlobal does
     */
    private static final class DetachedDialog extends Dialog {
        int dismissCalls;

        DetachedDialog() {
            super(null);
        }

        @Override
        public boolean isShowing() {
            return true;
        }

        @Override
        public void dismiss() {
            dismissCalls++;
            throw new IllegalArgumentException("View=DecorView not attached to window manager");
        }
    }

    @Test
    public void dismissingADetachedDialogDoesNotThrow() {
        DetachedDialog dialog = new DetachedDialog();
        SafeDialogs.dismiss(dialog);
        assertEquals(1, dialog.dismissCalls);
    }

    @Test
    public void nothingToDismissIsFine() {
        SafeDialogs.dismiss((Dialog) null);
        SafeDialogs.dismiss((RebornProgressDialog) null);
    }

    @Test
    public void noScreenNoDialog() {
        assertFalse(SafeDialogs.canShow(null));
    }
}
