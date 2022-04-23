package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.services.JPackageService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintStream;

public class JPackageGUIController implements Runnable {
    private File packageJSONFile;
    private PrintStream out = System.out;
    private PrintStream err = System.err;
    private JFrame parentFrame;
    private JDialog progressDialog;

    private JProgressBar progressBar;

    public JPackageGUIController(JFrame parentFrame, File packageJSONFile) {
        this.parentFrame = parentFrame;
        this.packageJSONFile = packageJSONFile;
    }

    @Override
    public void run() {
        //try {
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(100, 15));
        JPanel progressBarWrapper = new JPanel();
        progressBarWrapper.setLayout(new FlowLayout(FlowLayout.CENTER));
        progressBarWrapper.add(progressBar);
        JPanel body = new JPanel();
        JLabel message = new JLabel("<html>Generating native bundle for current platform using jpackage. This may take a couple of minutes</html");
        message.setPreferredSize(new Dimension(300, 50));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(progressBarWrapper);
        body.add(message);
        JOptionPane optionPane = new JOptionPane(body, JOptionPane.INFORMATION_MESSAGE, JOptionPane.CANCEL_OPTION);
        progressDialog = optionPane.createDialog(parentFrame,"jpackage Progress");

        SwingWorker worker = new SwingWorker() {

            private Exception error;

            @Override
            protected Object doInBackground() throws Exception {
                try {
                    JPackageService jPackageService = new JPackageService(packageJSONFile, null);
                    jPackageService.execute();
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (progressDialog != null) {
                    progressDialog.dispose();
                    progressDialog = null;
                }
                if (error != null) {
                    error.printStackTrace(err);
                    JOptionPane.showMessageDialog(parentFrame, "jpackage failed.  See console for stack trace", "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(parentFrame, "Package generation was successful.", "Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(new File(packageJSONFile.getParentFile(), "jdeploy" + File.separator + "jpackage"));
                        } catch (Exception ex) {
                            ex.printStackTrace(err);
                        }
                    }
                }
            }


        };
        worker.execute();
        progressDialog.setModal(true);
        progressDialog.setVisible(true);
        if (!worker.isDone()) {
            worker.cancel(true);
        }

    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }
}
