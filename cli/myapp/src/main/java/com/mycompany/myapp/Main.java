package com.example.smoke;

/**
 * Minimal Java entrypoint for the jdeploy-js-smoke test project.
 * Prints a unique marker and echoes any provided command-line arguments.
 */
public class Main {
    public static void main(String[] args) {
        // Marker to verify the Java app executed
        System.out.println("JDEPLOY_JS_SMOKE_OK");

        // Echo args in a single line prefixed with ARGS:
        if (args == null || args.length == 0) {
            System.out.println("ARGS:");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("ARGS:");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(args[i]);
            }
            System.out.println(sb.toString());
        }
    }
}
