/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.tabs;

import java.util.ArrayList;
import java.util.List;

import projekt.substratum.adapters.tabs.overlays.OverlaysItem;

class OverlaysInstance {

    private static final OverlaysInstance ourInstance = new OverlaysInstance();
    boolean hasFailed;
    Integer failCount;
    StringBuilder failedPackages;
    StringBuilder errorLogs;
    boolean missingType3;
    List<String> finalRunner;
    List<String> lateInstall;
    ArrayList<String> finalCommand;
    List<OverlaysItem> checkedOverlays;
    double currentAmount;
    double totalAmount;
    int overlaysWaiting;
    // One-tap Apply bookkeeping: overlay names that compiled, labels of those that did not
    List<String> builtOverlays;
    List<String> failedLabels;
    // Lines of the theme the self-healing compile left out ("App · file: name (reference)")
    List<String> pruned;
    /**
     * A compile or apply is running. The instance outlives the screen: applying a theme can
     * recreate it, and the new screen must not wipe what the running apply still works with.
     */
    volatile boolean running;
    private volatile long startedAt;

    /**
     * Obtain the current instance
     *
     * @return Returns the current instance
     */
    static OverlaysInstance getInstance() {
        // Reset only when nothing runs: a screen recreated during an apply joins it
        if (!ourInstance.isBusy()) ourInstance.reset();
        return ourInstance;
    }

    /**
     * A compile or apply started less than 15 minutes ago and has not finished
     */
    boolean isBusy() {
        return running && System.currentTimeMillis() - startedAt < 15 * 60 * 1000L;
    }

    void start() {
        startedAt = System.currentTimeMillis();
        running = true;
    }

    void finish() {
        running = false;
    }

    /**
     * Resets the singleton instance and its values
     */
    void reset() {
        hasFailed = false;
        failCount = 0;
        failedPackages = new StringBuilder();
        errorLogs = new StringBuilder();
        missingType3 = false;
        finalRunner = new ArrayList<>();
        lateInstall = new ArrayList<>();
        finalCommand = new ArrayList<>();
        checkedOverlays = new ArrayList<>();
        currentAmount = 0;
        overlaysWaiting = 0;
        builtOverlays = new ArrayList<>();
        failedLabels = new ArrayList<>();
        pruned = new ArrayList<>();
    }
}