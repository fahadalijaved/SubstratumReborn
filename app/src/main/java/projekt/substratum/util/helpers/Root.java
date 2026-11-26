/*
 * Copyright (c) 2016-2019 Projekt Substratum
 * This file is part of Substratum.
 *
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.util.helpers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import projekt.substratum.common.Systems;

public class Root {

    private static SU su;

    /**
     * Checks whether there is root access on the device
     *
     * @return True, if root is present
     */
    public static boolean checkRootAccess() {
        return Systems.checkRootAccess();
    }

    /**
     * Whether a root shell works right now. Unlike {@link #checkRootAccess()}, never answered
     * from the cached result: a temporary root can be gone after a reboot.
     *
     * @return True, if a command ran as uid 0
     */
    public static boolean isAlive() {
        String out = runCommand("id -u");
        return out != null && out.trim().equals("0");
    }

    /**
     * Request root access
     *
     * @return True, if su is granted
     */
    public static boolean requestRootAccess() {
        SU su = getSU();
        su.runCommand("echo /testRoot/");
        return !su.denied;
    }

    /**
     * Run a series of commands as su
     *
     * @return String output
     */
    public static String runCommand(String command) {
        return getSU().runCommand(command);
    }

    /**
     * Obtain a process of SU
     *
     * @return Instance of SU
     */
    private static synchronized SU getSU() {
        // Synchronized: the job queue, screens and the state migration all use root at once,
        // and each would otherwise start a shell of its own (one left running, never closed)
        if ((su == null) || su.closed || su.denied)
            su = new SU();
        return su;
    }

    /**
     * Class that is the beef of obtaining superuser access and permissions
     */
    private static class SU {
        private static final String TAG = "SubstratumRoot";
        // Longer than any single root command takes (theme installs, a full overlay dump)
        private static final long TIMEOUT_S = 300;
        private static final ScheduledExecutorService WATCHDOG =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "RootWatchdog");
                    thread.setDaemon(true);
                    return thread;
                });

        private Process process;
        private BufferedWriter bufferedWriter;
        private BufferedReader bufferedReader;
        private volatile boolean closed;
        private volatile boolean denied;
        private volatile boolean timedOut;
        private boolean firstTry;

        SU() {
            super();
            try {
                firstTry = true;
                process = Runtime.getRuntime().exec("su");
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(
                        process.getOutputStream()));
                bufferedReader = new BufferedReader(new InputStreamReader(
                        process.getInputStream()));
            } catch (IOException ignored) {
                denied = true;
                closed = true;
            }
        }

        synchronized String runCommand(String command) {
            if (command == null || command.trim().isEmpty()) return "";
            ScheduledFuture<?> watchdog = null;
            timedOut = false;
            try {
                StringBuilder sb = new StringBuilder();
                String callback = "/shellCallback/";
                // The shell's stdin carries the next commands and the end marker: a command that
                // reads stdin would swallow them and this read would wait forever, so every
                // command gets /dev/null as its stdin. Braces keep it in the same shell.
                bufferedWriter.write("{\n" + command + "\n} </dev/null\necho " + callback + '\n');
                bufferedWriter.flush();
                final Process shell = process;
                watchdog = WATCHDOG.schedule(() -> {
                    timedOut = true;
                    Log.e(TAG, "root command still running after " + TIMEOUT_S +
                            " s, restarting the root shell");
                    // The shell runs as root, so the app cannot kill it: closing the pipes ends
                    // the blocked read here (and an idle shell exits on end of input)
                    try {
                        shell.getOutputStream().close();
                    } catch (Exception ignored) {
                    }
                    try {
                        shell.getInputStream().close();
                    } catch (Exception ignored) {
                    }
                    shell.destroy();
                }, TIMEOUT_S, TimeUnit.SECONDS);

                char[] buffer = new char[4096];
                int from = 0;
                while (true) {
                    int read = bufferedReader.read(buffer);
                    if (read < 0) {
                        // The shell ended: su refused, the command exited it, or the watchdog
                        closed = true;
                        if (firstTry && !timedOut) denied = true;
                        return null;
                    }
                    sb.append(buffer, 0, read);
                    int i = sb.indexOf(callback, Math.max(0, from - callback.length()));
                    if (i > -1) {
                        sb.delete(i, i + callback.length());
                        break;
                    }
                    from = sb.length();
                }
                firstTry = false;
                return sb.toString().trim();
            } catch (IOException ignored) {
                closed = true;
                if (firstTry && !timedOut) denied = true;
            } catch (Exception ignored) {
                denied = true;
            } finally {
                if (watchdog != null) watchdog.cancel(false);
            }
            return null;
        }

    }
}