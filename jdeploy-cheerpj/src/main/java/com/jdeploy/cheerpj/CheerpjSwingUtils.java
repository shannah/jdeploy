package com.jdeploy.cheerpj;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

public class CheerpjSwingUtils {
    private static final Object CLIENT_PROPERTY_MAIN_WINDOW = new Object();

    public static void launchNoArgs(String mainClass) {
        System.out.println("launchNoArgs: " + mainClass);
        try {
            // Load the class.
            Class<?> cls = Class.forName(mainClass);

            // Find the main method.
            Method mainMethod = cls.getDeclaredMethod("main", String[].class);

            // Ensure the method is public and static.
            int modifiers = mainMethod.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(modifiers) || !java.lang.reflect.Modifier.isStatic(modifiers)) {
                throw new IllegalAccessException("main method is not public and static");
            }

            // Call the main method with an empty string array.
            String[] params = new String[]{};
            mainMethod.invoke(null, (Object) params); // Cast to Object because the parameter is a vararg.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void onResize() {
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(CheerpjSwingUtils::onResize);
            return;
        }
        CheerpjSwingUtils utils = new CheerpjSwingUtils();
        JFrame f = utils.getMainWindow();
        if (f != null) {
            java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
            java.awt.Dimension screenSize = toolkit.getScreenSize();
            f.setBounds(0, 0, screenSize.width, screenSize.height);
            f.setPreferredSize(new java.awt.Dimension(screenSize.width, screenSize.height));
            f.pack();
        }
    }

    private javax.swing.JFrame getMainWindow() {
        java.awt.Window[] windows = java.awt.Window.getWindows();
        int len = windows.length;
        for (int i = 0; i < len; i++) {
            java.awt.Window w = windows[i];
            if (w instanceof javax.swing.JFrame) {
                javax.swing.JFrame f = (javax.swing.JFrame) w;
                if (!f.isVisible()) {
                    continue;
                }
                JRootPane rootPane = f.getRootPane();
                if (rootPane != null && rootPane.getClientProperty(CLIENT_PROPERTY_MAIN_WINDOW) != null) {
                    return f;
                }
            }
        }

        for (int i = 0; i < len; i++) {
            java.awt.Window w = windows[i];
            if (w instanceof javax.swing.JFrame) {
                JFrame f = (JFrame) w;
                if (!f.isVisible()) {
                    continue;
                }
                JRootPane rootPane = f.getRootPane();
                if (rootPane != null) {
                    rootPane.putClientProperty(CLIENT_PROPERTY_MAIN_WINDOW, Boolean.TRUE);
                    if (!f.isUndecorated()) {
                        try {
                            f.setUndecorated(true);
                        } catch (Exception ex) {
                            f.dispose();
                            f.setUndecorated(true);
                            f.setVisible(true);
                            f.pack();
                        }
                    }

                    return f;
                }
            }
        }

        return null;
    }
}
