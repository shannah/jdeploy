package ca.weblite.jdeploy.gui.tabs;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class CheerpJSettings {
    private JPanel content;
    private JCheckBox enableCheerpJ;
    private JTextField githubPagesBranch;
    private JTextField githubPagesBranchPath;
    private JPanel buttons;
    private JButton helpButton;
    private JPanel root;
    private JTextField githubPagesTagPath;
    private JTextField githubPagesPath;

    public JPanel getContent() {
        return content;
    }

    public JCheckBox getEnableCheerpJ() {
        return enableCheerpJ;
    }

    public JTextField getGithubPagesBranch() {
        return githubPagesBranch;
    }

    public JTextField getGithubPagesDirectory() {
        return githubPagesBranchPath;
    }

    public JPanel getButtons() {
        return buttons;
    }

    public JButton getHelpButton() {
        return helpButton;
    }

    public JPanel getRoot() {
        return root;
    }

    public JTextField getGithubPagesBranchPath() {
        return githubPagesBranchPath;
    }

    public JTextField getGithubPagesTagPath() {
        return githubPagesTagPath;
    }

    public JTextField getGithubPagesPath() {
        return githubPagesPath;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        root = new JPanel();
        root.setLayout(new BorderLayout(0, 0));
        root.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        content = new JPanel();
        content.setLayout(new com.jgoodies.forms.layout.FormLayout("fill:d:grow,left:4dlu:noGrow,fill:d:grow", "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        root.add(content, BorderLayout.CENTER);
        final JLabel label1 = new JLabel();
        label1.setText("<html><p>This section allows you to deploy your application as a web application using CheerpJ and Github Pages</p></html>");
        com.jgoodies.forms.layout.CellConstraints cc = new com.jgoodies.forms.layout.CellConstraints();
        content.add(label1, cc.xy(1, 1));
        final JLabel label2 = new JLabel();
        label2.setText("<html><p>This is experimental, and will only work if your project uses only libraries supported by CheerpJ.</p></html>");
        content.add(label2, cc.xy(1, 3));
        final JLabel label3 = new JLabel();
        label3.setText("<html><p>Currently Swing and AWT UIs are supported.  There is no support currently for JavaFX.</p></html>");
        content.add(label3, cc.xy(1, 5));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.jgoodies.forms.layout.FormLayout("fill:d:grow,left:4dlu:noGrow,fill:d:grow", "center:d:grow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        content.add(panel1, cc.xy(1, 7));
        final JLabel label4 = new JLabel();
        label4.setText("Github Pages Branch");
        panel1.add(label4, cc.xy(1, 5));
        githubPagesBranch = new JTextField();
        panel1.add(githubPagesBranch, cc.xy(3, 5, com.jgoodies.forms.layout.CellConstraints.FILL, com.jgoodies.forms.layout.CellConstraints.DEFAULT));
        enableCheerpJ = new JCheckBox();
        enableCheerpJ.setText("Enable Web App Generation using CheerpJ");
        panel1.add(enableCheerpJ, cc.xy(3, 3));
        final JLabel label5 = new JLabel();
        label5.setText("GitHub Pages Branch Path");
        panel1.add(label5, cc.xy(1, 9));
        githubPagesBranchPath = new JTextField();
        panel1.add(githubPagesBranchPath, cc.xy(3, 9, com.jgoodies.forms.layout.CellConstraints.FILL, com.jgoodies.forms.layout.CellConstraints.DEFAULT));
        final JLabel label6 = new JLabel();
        label6.setText("GitHub Pages Tag Path");
        panel1.add(label6, cc.xy(1, 15));
        githubPagesTagPath = new JTextField();
        panel1.add(githubPagesTagPath, cc.xy(3, 15, com.jgoodies.forms.layout.CellConstraints.FILL, com.jgoodies.forms.layout.CellConstraints.DEFAULT));
        final JLabel label7 = new JLabel();
        label7.setText("GitHub Pages Default Path");
        panel1.add(label7, cc.xy(1, 21));
        githubPagesPath = new JTextField();
        panel1.add(githubPagesPath, cc.xy(3, 21, com.jgoodies.forms.layout.CellConstraints.FILL, com.jgoodies.forms.layout.CellConstraints.DEFAULT));
        final JLabel label8 = new JLabel();
        label8.setText("E.g. gh-pages");
        panel1.add(label8, cc.xy(3, 7));
        final JLabel label9 = new JLabel();
        label9.setText("Sub-path in pages site where \"branch\" releases should be published.");
        panel1.add(label9, cc.xy(3, 11));
        final JLabel label10 = new JLabel();
        label10.setText("Use {{ branch }} as placeholder for the branch name.");
        panel1.add(label10, cc.xy(3, 13));
        final JLabel label11 = new JLabel();
        label11.setText("Sub-path in pages site where \"tag\" releases should be published.");
        panel1.add(label11, cc.xy(3, 17));
        final JLabel label12 = new JLabel();
        label12.setText("Use {{ tag }} as placeholder for the tag name.");
        panel1.add(label12, cc.xy(3, 19));
        final JLabel label13 = new JLabel();
        label13.setText("Alternative to \"Tag Path\" and \"Branch Path\" to use same sub-path for both release types");
        panel1.add(label13, cc.xy(3, 23));
        buttons = new JPanel();
        buttons.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        root.add(buttons, BorderLayout.NORTH);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return root;
    }

}
