package com.appbrim;

import javafx.application.Application;

/**
 * Classpath-safe entry point. When JavaFX lives on the classpath (not the module path),
 * the main class must not extend Application itself, so we redirect to App.
 */
public final class Launcher {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    private Launcher() {
    }
}
