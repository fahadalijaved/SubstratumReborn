/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.common.platform;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The module's safe forwarding (which drawables it may forward) and its crash attribution, with
 * the two System UI crashes a classic theme caused on One UI 9.
 */
public class SafeForwardingTest {

    private static final String RIPPLE = "android.graphics.drawable.RippleDrawable";
    private static final String RECOIL = "androidx.appcompat.graphics.drawable.SeslRecoilDrawable";
    private static final String VECTOR = "android.graphics.drawable.VectorDrawable";

    private static final String SYSUI = "com.android.systemui.DEEPDARKNESS.SamsungoneUI";
    private static final String NAVBARS = "com.android.systemui.navbars.DEEPDARKNESS.SamsungoneUI";

    // Classes

    @Test
    public void sameClassIsForwarded() {
        assertNull(ForwardCheck.drawable(VECTOR, VECTOR, true));
        assertNull(ForwardCheck.drawable(RECOIL, RECOIL, true));
    }

    @Test
    public void anAppClassIsKeptWhenTheThemeGivesAnother() {
        // ActivatableNotificationView.initBackground casts to SeslRecoilDrawable
        String verdict = ForwardCheck.drawable(RECOIL, RIPPLE, false);
        assertNotNull(verdict);
        assertTrue(verdict.contains("SeslRecoilDrawable"));
        // A subclass of what the app expects is fine
        assertNull(ForwardCheck.drawable(RIPPLE, RECOIL, true));
        assertNotNull(ForwardCheck.drawable("com.samsung.android.graphics.SemPathDrawable",
                VECTOR, false));
    }

    @Test
    public void plainFrameworkDrawablesMayChangeKind() {
        assertNull(ForwardCheck.drawable("android.graphics.drawable.GradientDrawable", VECTOR,
                false));
        assertNull(ForwardCheck.drawable("android.graphics.drawable.BitmapDrawable",
                "android.graphics.drawable.NinePatchDrawable", false));
    }

    @Test
    public void animatedDrawablesKeepTheirKind() {
        assertNotNull(ForwardCheck.drawable("android.graphics.drawable.AnimatedVectorDrawable",
                VECTOR, false));
        assertNotNull(ForwardCheck.drawable("android.graphics.drawable.LevelListDrawable",
                VECTOR, false));
    }

    @Test
    public void whatDoesNotLoadIsNeverForwarded() {
        assertNotNull(ForwardCheck.drawable(VECTOR, null, false));
        // The app has none to compare with: the theme's own is used
        assertNull(ForwardCheck.drawable(null, VECTOR, false));
    }

    // Attribution

    private static final List<CrashAttribution.Forwarded> FORWARDED = Arrays.asList(
            new CrashAttribution.Forwarded(SYSUI, SYSUI, "drawable/notification_material_bg",
                    RIPPLE, RECOIL),
            new CrashAttribution.Forwarded(SYSUI, SYSUI, "drawable/notification_material_bg_dim",
                    RIPPLE, RECOIL),
            new CrashAttribution.Forwarded(SYSUI, SYSUI, "layout/status_bar_expanded",
                    null, null),
            new CrashAttribution.Forwarded(NAVBARS, NAVBARS, "drawable/ic_sysbar_docked",
                    null, null),
            new CrashAttribution.Forwarded(NAVBARS, NAVBARS, "drawable/ic_sysbar_back",
                    VECTOR, VECTOR));

    /**
     * Crash 1 (13:25): notification backgrounds forwarded as plain RippleDrawables
     */
    private static final String CRASH_RECOIL =
            "FATAL EXCEPTION: main\n" +
            "Process: com.android.systemui, PID: 4711\n" +
            "java.lang.ClassCastException: android.graphics.drawable.RippleDrawable cannot be " +
            "cast to androidx.appcompat.graphics.drawable.SeslRecoilDrawable\n" +
            "\tat com.android.systemui.statusbar.notification.row.ActivatableNotificationView" +
            ".initBackground(ActivatableNotificationView.java:211)\n" +
            "\tat com.android.systemui.statusbar.notification.row.ActivatableNotificationView" +
            ".onFinishInflate(ActivatableNotificationView.java:178)\n" +
            "\tat com.android.systemui.statusbar.notification.row.ExpandableNotificationRow" +
            ".onFinishInflate(ExpandableNotificationRow.java:2010)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:1212)\n";

    /**
     * Crash 2 (13:31): the navigation bar's docked icon did not inflate
     */
    private static final String CRASH_NAVBAR =
            "FATAL EXCEPTION: main\n" +
            "Process: com.android.systemui, PID: 4802\n" +
            "android.view.InflateException: Binary XML file line #42 in " +
            "com.android.systemui:layout/samsung_navigation_bar: Error inflating class " +
            "com.android.systemui.navigationbar.buttons.KeyButtonView\n" +
            "Caused by: java.lang.reflect.InvocationTargetException\n" +
            "\tat java.lang.reflect.Constructor.newInstance0(Native Method)\n" +
            "Caused by: android.content.res.Resources$NotFoundException: Drawable " + NAVBARS +
            ":drawable/ic_sysbar_docked with resource ID #0x7f080004\n" +
            "Caused by: java.lang.NullPointerException: Attempt to invoke interface method " +
            "'java.lang.String java.lang.CharSequence.toString()' on a null object reference\n" +
            "\tat android.content.res.ResourcesImpl.loadXmlDrawable(ResourcesImpl.java:1045)\n";

    @Test
    public void aClassCastPointsAtTheForwardedDrawable() {
        CrashAttribution.Result result = CrashAttribution.attribute(CRASH_RECOIL, FORWARDED);
        assertNotNull(result);
        assertEquals(SYSUI, result.overlay);
        assertTrue(result.names.contains("drawable/notification_material_bg"));
        assertTrue(result.names.contains("drawable/notification_material_bg_dim"));
    }

    @Test
    public void aDrawableThatDoesNotInflatePointsAtItsPart() {
        CrashAttribution.Result result = CrashAttribution.attribute(CRASH_NAVBAR, FORWARDED);
        assertNotNull(result);
        assertEquals(NAVBARS, result.overlay);
        assertTrue(result.names.contains("drawable/ic_sysbar_docked"));
    }

    @Test
    public void aCrashThatNamesNoPartIsNotAttributed() {
        String unrelated = "java.lang.IllegalStateException: Fragment not attached\n" +
                "\tat com.android.systemui.qs.QSFragment.getContext(QSFragment.java:10)\n";
        assertNull(CrashAttribution.attribute(unrelated, FORWARDED));
        assertNull(CrashAttribution.attribute(CRASH_NAVBAR, Arrays.asList()));
    }

    @Test
    public void theRecordReadsBack() {
        CrashAttribution.Result result = CrashAttribution.attribute(CRASH_NAVBAR, FORWARDED);
        String file = CrashAttribution.Recorded.format(result, 1764150000000L,
                "android.view.InflateException: line 1\nline 2");
        CrashAttribution.Recorded back = CrashAttribution.Recorded.parse(file);
        assertNotNull(back);
        assertEquals(NAVBARS, back.overlay);
        assertTrue(back.names.contains("drawable/ic_sysbar_docked"));
        assertEquals(1764150000000L, back.time);
        assertTrue(back.error.startsWith("android.view.InflateException"));
        assertNull(CrashAttribution.Recorded.parse(""));
        assertNull(CrashAttribution.Recorded.parse(null));
    }

    @Test
    public void onlyTheCrashBeforeTheTripNamesThePart() {
        String record = "overlay=" + NAVBARS + "\nnames=drawable/ic_sysbar_docked\n" +
                "time=1764150000000\n\n";
        // What the app reads: the record, then the guard's own time
        CrashAttribution.Recorded fresh = CrashAttribution.Recorded.parse(record +
                "tripped=1764150040000\n");
        assertTrue(fresh.explainsTrip(10 * 60_000L));
        // A record left from an older crash names the wrong part
        CrashAttribution.Recorded old = CrashAttribution.Recorded.parse(record +
                "tripped=1764160000000\n");
        assertTrue(!old.explainsTrip(10 * 60_000L));
        // No trip time to compare with: the record is all there is
        assertTrue(CrashAttribution.Recorded.parse(record + "tripped=\n")
                .explainsTrip(10 * 60_000L));
    }

    @Test
    public void manyCopiesOfTheSameEntriesStillPointAtOnePart() {
        java.util.List<CrashAttribution.Forwarded> many = new java.util.ArrayList<>();
        for (int i = 0; i < 2000; i++) many.addAll(FORWARDED);
        CrashAttribution.Result result = CrashAttribution.attribute(CRASH_NAVBAR, many);
        assertNotNull(result);
        assertEquals(NAVBARS, result.overlay);
    }
}
