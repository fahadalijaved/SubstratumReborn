/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.compilers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The self-healing compile: aapt's errors (the ones a classic theme gave on One UI 9) and sample
 * XML written for this test. Only the offending element goes; the rest stays; the loop ends.
 */
public class CompileRepairTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static List<String> lines(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }

    private static String error(String file, int line, String message) {
        return "/data/user/0/projekt.substratum/cache/SubstratumBuilder//type3_Samsung_oneUI/" +
                file + ':' + line + ": error: " + message;
    }

    @Test
    public void parsesTheErrorsSeenOnTheDevice() {
        List<CompileRepair.Problem> problems = CompileRepair.parse(Arrays.asList(
                error("values/settings_styles.xml", 49, "Error: No resource found that matches " +
                        "the given name (at 'android:actionBarTheme' with value " +
                        "'@*android:style/ThemeOverlay.DeviceDefault.ActionBar.Accent')."),
                error("values/colors.xml", 131, "Error: No resource found that matches the " +
                        "given name (at 'link_text_material_light' with value " +
                        "'@*com.google.android.gm:color/accent_blue')."),
                error("values/styles.xml", 12, "Error retrieving parent for item: No resource " +
                        "found that matches the given name 'Theme.Gallery.Base'."),
                error("layout/main.xml", 3, "resource android:color/gone_color (aka " +
                        "com.x:color/gone_color) not found."),
                "Installation of \"android\" has failed."));
        assertEquals(4, problems.size());
        assertEquals("android:actionBarTheme", problems.get(0).attr);
        assertEquals("@*android:style/ThemeOverlay.DeviceDefault.ActionBar.Accent",
                problems.get(0).ref);
        assertEquals(49, problems.get(0).line);
        assertEquals("link_text_material_light", problems.get(1).attr);
        assertTrue(problems.get(2).parent);
        assertEquals("Theme.Gallery.Base", problems.get(2).ref);
        assertEquals("android:color/gone_color", problems.get(3).ref);
    }

    @Test
    public void removesOnlyTheStyleItem() {
        List<String> xml = lines(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <style name=\"Theme.Settings\" parent=\"@android:style/Theme.DeviceDefault\">",
                "        <item name=\"android:colorAccent\">#ff00bcd4</item>",
                "        <item name=\"android:actionBarTheme\">@*android:style/ThemeOverlay.DeviceDefault.ActionBar.Accent</item>",
                "        <item name=\"android:windowBackground\">@color/bg</item>",
                "    </style>",
                "</resources>");
        List<CompileRepair.Pruned> out = new ArrayList<>();
        List<String> result = CompileRepair.prune(xml, "values", CompileRepair.parse(
                Collections.singletonList(error("values/settings_styles.xml", 5,
                        "Error: No resource found that matches the given name (at " +
                                "'android:actionBarTheme' with value '@*android:style/" +
                                "ThemeOverlay.DeviceDefault.ActionBar.Accent').")))
                , "values/settings_styles.xml", out);
        String text = String.join("\n", result);
        assertFalse(text.contains("actionBarTheme"));
        assertTrue(text.contains("android:colorAccent"));
        assertTrue(text.contains("android:windowBackground"));
        assertTrue(text.contains("<style name=\"Theme.Settings\""));
        assertTrue(text.contains("</style>"));
        assertEquals(1, out.size());
        assertEquals("Theme.Settings/android:actionBarTheme", out.get(0).name);
    }

    @Test
    public void removesAValueThatIsAMissingReference() {
        List<String> xml = lines(
                "<resources>",
                "    <color name=\"primary\">#ff101010</color>",
                "    <color name=\"link_text_material_light\">@*com.google.android.gm:color/accent_blue</color>",
                "    <color name=\"material_grey_50\">@*android:color/secondary_text_leanback_light</color>",
                "    <color name=\"background\">#ff000000</color>",
                "</resources>");
        List<CompileRepair.Pruned> out = new ArrayList<>();
        List<String> result = CompileRepair.prune(xml, "values", CompileRepair.parse(Arrays.asList(
                error("values/colors.xml", 3, "Error: No resource found that matches the given " +
                        "name (at 'link_text_material_light' with value " +
                        "'@*com.google.android.gm:color/accent_blue')."),
                error("values/colors.xml", 4, "Error: No resource found that matches the given " +
                        "name (at 'material_grey_50' with value " +
                        "'@*android:color/secondary_text_leanback_light')."))),
                "values/colors.xml", out);
        assertEquals(lines("<resources>",
                "    <color name=\"primary\">#ff101010</color>",
                "    <color name=\"background\">#ff000000</color>",
                "</resources>"), result);
        assertEquals(2, out.size());
        assertEquals("link_text_material_light", out.get(0).name);
        assertEquals("@*com.google.android.gm:color/accent_blue", out.get(0).ref);
    }

    @Test
    public void aMissingParentGoesFirstThenTheStyle() {
        List<String> xml = lines(
                "<resources>",
                "    <style name=\"GalleryTheme\"",
                "        parent=\"@*com.sec.android.gallery3d:style/Theme.Gallery.Base\">",
                "        <item name=\"android:colorAccent\">#ff00bcd4</item>",
                "    </style>",
                "</resources>");
        String parentError = error("values/styles.xml", 2, "Error retrieving parent for item: " +
                "No resource found that matches the given name " +
                "'@*com.sec.android.gallery3d:style/Theme.Gallery.Base'.");
        List<CompileRepair.Pruned> out = new ArrayList<>();
        List<String> first = CompileRepair.prune(xml, "values",
                CompileRepair.parse(Collections.singletonList(parentError)), "values/styles.xml",
                out);
        String text = String.join("\n", first);
        assertTrue(text.contains("parent=\"\""));
        assertTrue(text.contains("android:colorAccent"));
        assertEquals(1, out.size());

        // Still failing: the style goes, the file stays valid
        List<String> second = CompileRepair.prune(first, "values",
                CompileRepair.parse(Collections.singletonList(parentError)), "values/styles.xml",
                out);
        assertEquals(lines("<resources>", "</resources>"), second);
        assertEquals(2, out.size());
    }

    @Test
    public void aLayoutLosesOnlyTheAttribute() {
        List<String> xml = lines(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    android:layout_width=\"match_parent\"",
                "    android:layout_height=\"wrap_content\"",
                "    android:background=\"@*com.samsung.android.lool:drawable/abc_ic_ab_back_material\"",
                "    android:orientation=\"vertical\">",
                "    <TextView android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" />",
                "</LinearLayout>");
        List<CompileRepair.Pruned> out = new ArrayList<>();
        List<String> result = CompileRepair.prune(xml, "layout", CompileRepair.parse(
                Collections.singletonList(error("layout/card.xml", 1, "Error: No resource found " +
                        "that matches the given name (at 'background' with value " +
                        "'@*com.samsung.android.lool:drawable/abc_ic_ab_back_material')."))),
                "layout/card.xml", out);
        String text = String.join("\n", result);
        assertFalse(text.contains("android:background"));
        assertTrue(text.contains("android:orientation=\"vertical\""));
        assertTrue(text.contains("<TextView"));
        assertEquals(xml.size(), result.size());
        assertEquals("android:background", out.get(0).name);
    }

    @Test
    public void anErrorReportedTwiceKeepsTheFile() {
        List<String> xml = lines(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    android:layout_width=\"match_parent\"",
                "    android:layout_height=\"wrap_content\"",
                "    android:background=\"@*com.samsung.android.lool:drawable/abc_ic_ab_back_material\">",
                "</LinearLayout>");
        String error = error("layout/card.xml", 1, "Error: No resource found that matches the " +
                "given name (at 'background' with value " +
                "'@*com.samsung.android.lool:drawable/abc_ic_ab_back_material').");
        List<CompileRepair.Pruned> out = new ArrayList<>();
        List<String> result = CompileRepair.prune(xml, "layout",
                CompileRepair.parse(Arrays.asList(error, error)), "layout/card.xml", out);
        // The second report finds its attribute gone: the file stays, without it
        assertTrue(result != null);
        assertFalse(String.join("\n", result).contains("android:background"));
        assertEquals(1, out.size());
    }

    @Test
    public void aFileThatNeedsTheAttributeGoes() {
        List<String> xml = lines(
                "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    android:width=\"24dp\" android:height=\"24dp\"",
                "    android:viewportWidth=\"24\" android:viewportHeight=\"24\">",
                "    <path android:pathData=\"@*android:string/gone_path\" />",
                "</vector>");
        assertNull(CompileRepair.prune(xml, "drawable", CompileRepair.parse(
                Collections.singletonList(error("drawable/ic_sysbar_docked.xml", 4, "Error: No " +
                        "resource found that matches the given name (at 'pathData' with value " +
                        "'@*android:string/gone_path')."))), "drawable/ic_sysbar_docked.xml",
                new ArrayList<>()));
    }

    // The loop, against a stand-in compiler

    private static final Pattern REF = Pattern.compile("@\\*?([\\w.]+:)?(\\w+)/([\\w.]+)");

    /**
     * A tiny aapt: every reference to something in [missing] is an error at its line
     */
    private static List<String> compile(File dir, Set<String> missing) throws IOException {
        List<String> errors = new ArrayList<>();
        File[] folders = dir.listFiles();
        if (folders == null) return errors;
        Arrays.sort(folders);
        for (File folder : folders) {
            File[] files = folder.listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File file : files) {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = REF.matcher(lines.get(i));
                    while (m.find()) {
                        if (!missing.contains(m.group(3))) continue;
                        Matcher name = Pattern.compile("name=\"([^\"]+)\"").matcher(lines.get(i));
                        String at = name.find() ? name.group(1) : "background";
                        errors.add(file.getAbsolutePath() + ':' + (i + 1) + ": error: Error: No " +
                                "resource found that matches the given name (at '" + at +
                                "' with value '" + m.group() + "').");
                        // aapt stops a file at its first error
                        i = lines.size();
                        break;
                    }
                }
            }
        }
        return errors;
    }

    @Test
    public void theLoopConvergesAndStops() throws IOException {
        File work = tmp.newFolder("SubstratumBuilder");
        File values = new File(work, "values");
        File layout = new File(work, "layout");
        assertTrue(values.mkdirs() && layout.mkdirs());
        Files.write(new File(values, "colors.xml").toPath(), lines(
                "<resources>",
                "    <color name=\"a\">@*android:color/gone_a</color>",
                "    <color name=\"b\">#ff000000</color>",
                "    <color name=\"c\">@*android:color/gone_c</color>",
                "</resources>"), StandardCharsets.UTF_8);
        Files.write(new File(values, "styles.xml").toPath(), lines(
                "<resources>",
                "    <style name=\"S\">",
                "        <item name=\"android:x\">@*android:style/gone_x</item>",
                "        <item name=\"android:y\">@*android:style/gone_y</item>",
                "        <item name=\"android:z\">#ff00ff00</item>",
                "    </style>",
                "</resources>"), StandardCharsets.UTF_8);
        Files.write(new File(layout, "row.xml").toPath(), lines(
                "<FrameLayout",
                "    android:layout_width=\"match_parent\"",
                "    android:background=\"@*android:drawable/gone_bg\"",
                "    android:layout_height=\"48dp\" />"), StandardCharsets.UTF_8);
        Set<String> missing = new TreeSet<>(Arrays.asList("gone_a", "gone_c", "gone_x", "gone_y",
                "gone_bg"));

        List<CompileRepair.Pruned> pruned = new ArrayList<>();
        int rounds = 0;
        List<String> errors = compile(work, missing);
        while (!errors.isEmpty() && rounds < CompileRepair.MAX_ROUNDS) {
            if (CompileRepair.repair(errors, pruned) == 0) break;
            rounds++;
            errors = compile(work, missing);
        }
        assertTrue("converged", errors.isEmpty());
        assertTrue(rounds <= CompileRepair.MAX_ROUNDS);
        assertEquals(5, pruned.size());
        String colors = String.join("\n", Files.readAllLines(new File(values, "colors.xml")
                .toPath()));
        assertTrue(colors.contains("name=\"b\""));
        assertFalse(colors.contains("gone_"));
        String styles = String.join("\n", Files.readAllLines(new File(values, "styles.xml")
                .toPath()));
        assertTrue(styles.contains("android:z"));
        assertTrue(styles.contains("<style name=\"S\">"));
        assertTrue(new File(layout, "row.xml").isFile());

        // An error it cannot place: the round prunes nothing, and the loop stops there
        assertEquals(0, CompileRepair.repair(Collections.singletonList(
                work.getAbsolutePath() + "/values/colors.xml:1: error: something else"),
                pruned));
        assertEquals(0, CompileRepair.repair(Collections.singletonList("not an aapt line"),
                pruned));
    }
}
