package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.models.DeveloperIdentities;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

public class DeveloperIdentityPanel extends JPanel {
    private DeveloperIdentities identities;
    private AppInfo appInfo;

    public DeveloperIdentityPanel(DeveloperIdentities identities, AppInfo appInfo) {
        this.identities = identities;
        this.appInfo = appInfo;
        buildUI();
    }

    private JPanel flow(Component... components) {
        JPanel out = new JPanel();
        out.setLayout(new FlowLayout(FlowLayout.LEFT));
        for (Component cmp : components) {
            out.add(cmp);
        }
        return out;
    }

    private JPanel center(Component... components) {
        JPanel out = new JPanel();
        out.setLayout(new FlowLayout(FlowLayout.CENTER));
        for (Component cmp : components) {
            out.add(cmp);
        }
        return out;
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        DeveloperIdentity mainId = identities.getMainIdentity();
        JLabel installMessage = new JLabel("You are installing " + appInfo.getTitle() + " created by " + mainId.getName());
        JLabel websiteUrl = new JLabel(mainId.getWebsiteURL().substring(mainId.getWebsiteURL().indexOf("://")+3));
        FontIcon.of(Material.WARNING);
        JButton visitWebsite = new JButton("Visit Website");
        visitWebsite.addActionListener(evt->{
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(identities.getMainIdentity().getWebsiteURL()));
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                }
            }
        });

        JLabel onlyTrusted = new JLabel("You should only install software from trusted sources.  Do you trust this developer?");
        JButton yes = new JButton("Yes, Proceed with installation");
        JButton no = new JButton("No.  Cancel installation");

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        center.add(installMessage);
        center.add(flow(websiteUrl, visitWebsite));
        center.add(onlyTrusted);
        center.add(center(no, yes));
        add(center, BorderLayout.CENTER);



    }
}
