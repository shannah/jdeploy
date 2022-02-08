package ca.weblite.jdeploy.gui;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;

public class JDeployMainMenu {
    private JFrame frame;


    private void initFrame() {
        frame = new JFrame("JDeploy Main Menu");
        initUI(frame.getContentPane());


    }

    public void show() {
        initFrame();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void initUI(Container cnt) {

        JButton open = new JButton("Open Existing Project");
        open.addActionListener(evt->handleOpenProject(evt));

        cnt.removeAll();
        cnt.setLayout(new BoxLayout(cnt, BoxLayout.Y_AXIS));
        cnt.add(open);



    }

    private void handleOpenProject(ActionEvent evt) {
        FileDialog fd = new FileDialog(frame, "Select package.json", FileDialog.LOAD);
        fd.setFilenameFilter(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.equals("package.json");
            }
        });
        fd.setVisible(true);
        File[] selected = fd.getFiles();
        if (selected == null || selected.length == 0) {
            return;
        }

        File f = selected[0];
        if (!f.exists()) return;
        handleOpenProject(f);
    }

    private void handleOpenProject(File f) {
        try {
            String json = FileUtils.readFileToString(f, "UTF-8");
            JSONObject packageJson = new JSONObject(json);
            JDeployProjectEditor editor = new JDeployProjectEditor(f, packageJson);
            editor.show();
        } catch (IOException ex) {
            System.err.println("Failed to open project: "+ex.getMessage());
            ex.printStackTrace(System.err);
            showError("Failed to open project: "+ex.getMessage(), ex);
        }
    }


    private void showError(String message, Throwable e) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

}
