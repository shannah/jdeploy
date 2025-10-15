import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Simple test application to verify directory associations work correctly.
 * This app displays the command-line arguments received, which should include
 * directory paths when directories are opened with this app.
 */
public class DirectoryTestApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Directory Association Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);

            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            StringBuilder sb = new StringBuilder();
            sb.append("Directory Association Test App\n");
            sb.append("================================\n\n");
            sb.append("Number of arguments: ").append(args.length).append("\n\n");

            if (args.length == 0) {
                sb.append("No arguments received.\n");
                sb.append("Try:\n");
                sb.append("- Right-click on a folder and select 'Open with Directory Test App'\n");
                sb.append("- Drag a folder onto the app icon\n");
            } else {
                sb.append("Arguments received:\n\n");
                for (int i = 0; i < args.length; i++) {
                    sb.append(i + 1).append(". ").append(args[i]).append("\n");

                    File f = new File(args[i]);
                    if (f.exists()) {
                        sb.append("   - Exists: ").append(f.exists()).append("\n");
                        sb.append("   - Is Directory: ").append(f.isDirectory()).append("\n");
                        sb.append("   - Is File: ").append(f.isFile()).append("\n");
                        sb.append("   - Absolute Path: ").append(f.getAbsolutePath()).append("\n");
                    } else {
                        sb.append("   - Path does not exist\n");
                    }
                    sb.append("\n");
                }
            }

            textArea.setText(sb.toString());
            textArea.setCaretPosition(0);

            JScrollPane scrollPane = new JScrollPane(textArea);
            frame.add(scrollPane);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            System.out.println("=== Directory Association Test App Started ===");
            System.out.println("Arguments: " + args.length);
            for (int i = 0; i < args.length; i++) {
                System.out.println("  [" + i + "] " + args[i]);
            }
        });
    }
}
