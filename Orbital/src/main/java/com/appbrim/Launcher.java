package com.appbrim;

import javafx.application.Application;

/**
 * Classpath-safe entry point. When JavaFX lives on the classpath (not the module path),
 * the main class must not extend Application itself, so we redirect to App.
 */
public final class Launcher {
    public static void main(String[] args) {
        if (args.length > 0 && "--selftest".equals(args[0])) {
            // Diagnostics inside the packaged runtime: Orbital.exe --selftest [report.txt]
            System.exit(SelfTest.run(args.length > 1 ? java.nio.file.Path.of(args[1]) : null));
        }
        Application.launch(App.class, args);
    }

    private Launcher() {
    }
}
