package com.appbrim.hotkey;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * F9 global hotkey via RegisterHotKey on a dedicated message-loop thread.
 * Falls back silently (returns false) on non-Windows or if registration fails;
 * the UI always keeps an in-app trigger as a fallback.
 */
public final class GlobalHotkey {

    private interface User32Ext extends User32, StdCallLibrary {
        boolean RegisterHotKey(WinDef.HWND hWnd, int id, int fsModifiers, int vk);

        boolean UnregisterHotKey(WinDef.HWND hWnd, int id);

        int GetMessage(WinUser.MSG lpMsg, WinDef.HWND hWnd, WinDef.WPARAM wMsgFilterMin, WinDef.WPARAM wMsgFilterMax);
    }

    private static final int WM_HOTKEY = 0x0312;
    private static final int HOTKEY_ID = 0xB91;

    private static WinDef.HWND hwnd;
    private static User32Ext user32;
    private static final AtomicBoolean registered = new AtomicBoolean(false);

    /** Call once at startup; the runnable fires on the hotkey thread. */
    public static synchronized boolean register(int virtualKeyCode, Runnable onHotkey) {
        if (registered.get()) {
            return true;
        }
        try {
            user32 = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);
        } catch (Throwable t) {
            return false;
        }

        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            try {
                hwnd = user32.CreateWindowEx(0, "STATIC", "AppBrimHotkey", 0, 0, 0, 0, 0,
                        null, null, null, null);
                if (hwnd == null) {
                    ready.countDown();
                    return;
                }
                if (!user32.RegisterHotKey(hwnd, HOTKEY_ID, 0, virtualKeyCode)) {
                    ready.countDown();
                    return;
                }
                ok.set(true);
                ready.countDown();
                WinUser.MSG msg = new WinUser.MSG();
                while (true) {
                    int r = user32.GetMessage(msg, null, new WinDef.WPARAM(0), new WinDef.WPARAM(0));
                    if (r <= 0) {
                        break;
                    }
                    if (msg.message == WM_HOTKEY && msg.wParam.intValue() == HOTKEY_ID) {
                        try {
                            onHotkey.run();
                        } catch (RuntimeException ignored) {
                            // Hotkey handler must never kill the message loop.
                        }
                    }
                }
                user32.UnregisterHotKey(hwnd, HOTKEY_ID);
            } catch (Throwable ignored) {
                ready.countDown();
            }
        }, "appbrim-hotkey");
        t.setDaemon(true);
        t.start();
        try {
            ready.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        registered.set(ok.get());
        return ok.get();
    }
}
