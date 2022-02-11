package ca.weblite.jdeploy.gui;

import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

public class ProgressDialog {

    private JLabel message1, message2;
    private JProgressBar progressBar;
    private JTextArea output;
    private JSplitPane splitPane;
    private JDialog dialog;

    private String packageName;

    public ProgressDialog(String packageName) {
        this.packageName = packageName;
    }

    public void setMessage1(String message) {
        if (message1 != null) {
            message1.setText(message);
            dialog.revalidate();
        }
    }

    public void setMessage2(String message) {
        if (message2 != null) {
            message2.setText(message);
            dialog.revalidate();
        }
    }

    public void setComplete() {
        initCompletePanel((Container)splitPane.getTopComponent());
        ((Container)splitPane.getTopComponent()).revalidate();

    }

    public void setFailed() {
        initFailedPanel((Container)splitPane.getTopComponent());
        ((Container)splitPane.getTopComponent()).revalidate();
    }

    private void initFailedPanel(Container cnt) {
        JLabel message = new JLabel("<html>A problem occurred while trying to publish this package to npm.  Check the log below for clues.</html>");
        dialog.setTitle("Publish Failed");
        FontIcon failedIcon = FontIcon.of(Material.ERROR);
        failedIcon.setIconSize(50);
        JLabel failedIconLabel = new JLabel(failedIcon);
        failedIconLabel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel buttonsPanel = new JPanel();
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(evt->{
            dialog.dispose();
        });
        buttonsPanel.add(closeBtn);

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(failedIconLabel, BorderLayout.WEST);
        cnt.add(message, BorderLayout.CENTER);
        cnt.add(buttonsPanel, BorderLayout.SOUTH);

    }

    private void initCompletePanel(Container cnt) {
        JLabel message = new JLabel("<html>Published successfully</html>");
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        FontIcon doneIcon = FontIcon.of(Material.DONE);
        dialog.setTitle("Publish Succeeded");
        doneIcon.setIconSize(50);
        doneIcon.setIconColor(Color.green);
        JLabel successIcon = new JLabel(doneIcon);
        successIcon.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel buttonsPanel = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(evt->{
            dialog.dispose();
        });
        buttonsPanel.add(closeButton);

        if (packageName != null) {
            JButton downloadPage = new JButton("Visit Download Page");
            downloadPage.addActionListener(evt -> {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.jdeploy.com/~" + packageName));
                } catch (Exception ex) {
                    System.err.println("Failed to browse to download page");
                    ex.printStackTrace(System.err);
                }
            });
            buttonsPanel.add(downloadPage);
        }

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(successIcon, BorderLayout.WEST);
        cnt.add(message, BorderLayout.CENTER);
        cnt.add(buttonsPanel, BorderLayout.SOUTH);

    }

    private void initUI(Container cnt) {
        message1 = new JLabel();
        message2 = new JLabel();

        FontIcon uploadIcon = FontIcon.of(Material.FILE_UPLOAD);
        uploadIcon.setIconSize(50);
        JLabel uploadLabel = new JLabel(uploadIcon);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setBorder(new EmptyBorder(25, 25, 25, 25));
        output = new JTextArea();
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        JPanel top = new JPanel();
        top.setBorder(new EmptyBorder(15, 15, 15, 15));
        top.setLayout(new BorderLayout());

        top.add(message1, BorderLayout.CENTER);
        top.add(progressBar, BorderLayout.SOUTH);
        top.add(uploadLabel, BorderLayout.WEST);

        splitPane.setTopComponent(top);

        JScrollPane scrollPane = new JScrollPane(output);
        splitPane.setBottomComponent(scrollPane);

        cnt.removeAll();
        cnt.setLayout(new BorderLayout());
        cnt.add(splitPane, BorderLayout.CENTER);
        cnt.setPreferredSize(new Dimension(640, 480));




    }

    public void show(JFrame parentFrame, String title) {
        dialog = new JDialog(parentFrame, title);
        initUI(dialog.getContentPane());
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);

    }


    public void close() {
        //dialog.dispose();
    }

    public OutputStream createOutputStream() {
        return new OutputStream() {
            byte[] buffer = new byte[1024];
            int pos;

            private synchronized void ensureBuffer(int size) {
                if (pos + size < buffer.length) return;
                byte[] newbuf = new byte[buffer.length*2];
                System.arraycopy(buffer, 0, newbuf, 0, buffer.length);
                buffer = newbuf;
            }

            @Override
            public synchronized void write(int b) throws IOException {
                ensureBuffer(0);
                buffer[pos] = (byte)b;
                pos++;

            }

            @Override
            public synchronized void write(byte[] b) throws IOException {
                ensureBuffer(b.length);
                System.arraycopy(b, 0, buffer, pos, buffer.length);
                pos += buffer.length;
                flush();
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) throws IOException {
                ensureBuffer(len);
                System.arraycopy(b, off, buffer, pos, len);
                pos += len;
                flush();
            }

            @Override
            public void flush() throws IOException {
                String contents = new String(buffer, 0, pos, "UTF-8");
                pos = 0;
                EventQueue.invokeLater(()->{
                    if (output != null) {
                        output.setText(output.getText() + contents);
                    }
                });

            }
        };
    }



}
