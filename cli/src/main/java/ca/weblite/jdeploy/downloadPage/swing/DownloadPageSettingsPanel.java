package ca.weblite.jdeploy.downloadPage.swing;

import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings.BundlePlatform;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class DownloadPageSettingsPanel extends JPanel {
    private DownloadPageSettings settings;
    private final Map<BundlePlatform, JCheckBox> platformCheckboxes;
    private final List<ChangeListener> changeListeners;
    
    private JRadioButton allRadioButton;
    private JRadioButton defaultRadioButton;
    private JRadioButton customRadioButton;
    private ButtonGroup radioButtonGroup;
    private JPanel customPlatformPanel;
    
    public DownloadPageSettingsPanel() {
        this.settings = new DownloadPageSettings();
        this.platformCheckboxes = new HashMap<>();
        this.changeListeners = new ArrayList<>();
        initializeComponents();
    }
    
    public DownloadPageSettingsPanel(DownloadPageSettings settings) {
        this.settings = settings != null ? settings : new DownloadPageSettings();
        this.platformCheckboxes = new HashMap<>();
        this.changeListeners = new ArrayList<>();
        initializeComponents();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JLabel titleLabel = new JLabel("Platform Bundle Selection");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel radioPanel = createRadioButtonPanel();
        mainPanel.add(radioPanel, BorderLayout.CENTER);
        
        customPlatformPanel = createPlatformGrid();
        mainPanel.add(customPlatformPanel, BorderLayout.SOUTH);
        
        updateComponents();
        
        add(mainPanel, BorderLayout.NORTH);
    }
    
    private JPanel createRadioButtonPanel() {
        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        radioPanel.setBorder(BorderFactory.createTitledBorder("Platform Selection Mode"));
        
        radioButtonGroup = new ButtonGroup();
        
        allRadioButton = new JRadioButton("All Platforms");
        allRadioButton.setToolTipText("Provide download links for all available platforms");
        allRadioButton.addActionListener(new RadioButtonListener());
        radioButtonGroup.add(allRadioButton);
        radioPanel.add(allRadioButton);
        
        defaultRadioButton = new JRadioButton("Default Platforms");
        defaultRadioButton.setToolTipText("Provide download links for the default set of platforms as determined by the jDeploy website. Default platforms can change without notice.");
        defaultRadioButton.addActionListener(new RadioButtonListener());
        radioButtonGroup.add(defaultRadioButton);
        radioPanel.add(defaultRadioButton);
        
        customRadioButton = new JRadioButton("Custom Platforms");
        customRadioButton.setToolTipText("Select specific platforms to include on the download page");
        customRadioButton.addActionListener(new RadioButtonListener());
        radioButtonGroup.add(customRadioButton);
        radioPanel.add(customRadioButton);
        
        return radioPanel;
    }
    
    private JPanel createPlatformGrid() {
        JPanel gridPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel arm64Label = new JLabel("ARM64");
        arm64Label.setFont(arm64Label.getFont().deriveFont(Font.BOLD));
        arm64Label.setHorizontalAlignment(SwingConstants.CENTER);
        gridPanel.add(arm64Label, gbc);
        
        gbc.gridx = 2;
        JLabel x64Label = new JLabel("x86_64");
        x64Label.setFont(x64Label.getFont().deriveFont(Font.BOLD));
        x64Label.setHorizontalAlignment(SwingConstants.CENTER);
        gridPanel.add(x64Label, gbc);
        
        int row = 1;
        
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel windowsLabel = new JLabel("Windows:");
        windowsLabel.setFont(windowsLabel.getFont().deriveFont(Font.BOLD));
        windowsLabel.setToolTipText("Windows platform builds");
        gridPanel.add(windowsLabel, gbc);
        
        gbc.gridx = 1;
        JCheckBox windowsArm64 = createCheckbox(BundlePlatform.WindowsArm64);
        windowsArm64.setToolTipText("Windows ARM64 - Windows 10/11 on ARM processors");
        gridPanel.add(windowsArm64, gbc);
        
        gbc.gridx = 2;
        JCheckBox windowsX64 = createCheckbox(BundlePlatform.WindowsX64);
        windowsX64.setToolTipText("Windows x86_64 - Windows 7, 8, 10, and 11");
        gridPanel.add(windowsX64, gbc);
        
        row++;
        gbc.gridy = row;
        gbc.gridx = 0;
        JLabel macLabel = new JLabel("macOS:");
        macLabel.setFont(macLabel.getFont().deriveFont(Font.BOLD));
        macLabel.setToolTipText("Modern macOS platform builds");
        gridPanel.add(macLabel, gbc);
        
        gbc.gridx = 1;
        JCheckBox macArm64 = createCheckbox(BundlePlatform.MacArm64);
        macArm64.setToolTipText("macOS ARM64 (Apple Silicon) - macOS 10.14 through macOS 15 Sequoia");
        gridPanel.add(macArm64, gbc);
        
        gbc.gridx = 2;
        JCheckBox macX64 = createCheckbox(BundlePlatform.MacX64);
        macX64.setToolTipText("macOS x86_64 (Intel) - macOS 10.14 through macOS 15 Sequoia");
        gridPanel.add(macX64, gbc);
        
        row++;
        gbc.gridy = row;
        gbc.gridx = 0;
        JLabel macHighSierraLabel = new JLabel("macOS Legacy:");
        macHighSierraLabel.setFont(macHighSierraLabel.getFont().deriveFont(Font.BOLD));
        macHighSierraLabel.setToolTipText("Legacy macOS platform builds");
        gridPanel.add(macHighSierraLabel, gbc);
        
        gbc.gridx = 2;
        JCheckBox macHighSierra = createCheckbox(BundlePlatform.MacHighSierra);
        macHighSierra.setText("High Sierra+");
        macHighSierra.setToolTipText("macOS High Sierra+ x86_64 - macOS 10.10 through 10.13");
        gridPanel.add(macHighSierra, gbc);
        
        row++;
        gbc.gridy = row;
        gbc.gridx = 0;
        JLabel linuxLabel = new JLabel("Linux:");
        linuxLabel.setFont(linuxLabel.getFont().deriveFont(Font.BOLD));
        linuxLabel.setToolTipText("Generic Linux platform builds");
        gridPanel.add(linuxLabel, gbc);
        
        gbc.gridx = 1;
        JCheckBox linuxArm64 = createCheckbox(BundlePlatform.LinuxArm64);
        linuxArm64.setToolTipText("Linux ARM64 - Generic Linux distributions on ARM processors");
        gridPanel.add(linuxArm64, gbc);
        
        gbc.gridx = 2;
        JCheckBox linuxX64 = createCheckbox(BundlePlatform.LinuxX64);
        linuxX64.setToolTipText("Linux x86_64 - Generic Linux distributions on Intel/AMD processors");
        gridPanel.add(linuxX64, gbc);
        
        row++;
        gbc.gridy = row;
        gbc.gridx = 0;
        JLabel debianLabel = new JLabel("Debian:");
        debianLabel.setFont(debianLabel.getFont().deriveFont(Font.BOLD));
        debianLabel.setToolTipText("Debian-based platform builds");
        gridPanel.add(debianLabel, gbc);
        
        gbc.gridx = 1;
        JCheckBox debianArm64 = createCheckbox(BundlePlatform.DebianArm64);
        debianArm64.setToolTipText("Debian ARM64 - Debian/Ubuntu distributions on ARM processors");
        gridPanel.add(debianArm64, gbc);
        
        gbc.gridx = 2;
        JCheckBox debianX64 = createCheckbox(BundlePlatform.DebianX64);
        debianX64.setToolTipText("Debian x86_64 - Debian/Ubuntu distributions on Intel/AMD processors");
        gridPanel.add(debianX64, gbc);
        
        gridPanel.setBorder(BorderFactory.createTitledBorder("Platform Selection"));
        
        return gridPanel;
    }
    
    private JCheckBox createCheckbox(BundlePlatform platform) {
        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(settings.getEnabledPlatforms().contains(platform));
        checkbox.addActionListener(new PlatformCheckboxListener(platform));
        platformCheckboxes.put(platform, checkbox);
        return checkbox;
    }
    
    public DownloadPageSettings getSettings() {
        return settings;
    }
    
    public void setSettings(DownloadPageSettings settings) {
        this.settings = settings != null ? settings : new DownloadPageSettings();
        updateComponents();
    }
    
    private void updateComponents() {
        Set<BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        
        if (enabledPlatforms.contains(BundlePlatform.All)) {
            allRadioButton.setSelected(true);
            customPlatformPanel.setVisible(false);
        } else if (enabledPlatforms.contains(BundlePlatform.Default)) {
            defaultRadioButton.setSelected(true);
            customPlatformPanel.setVisible(false);
        } else {
            customRadioButton.setSelected(true);
            customPlatformPanel.setVisible(true);
            updateCheckboxes();
        }
        
        revalidate();
        repaint();
    }
    
    private void updateCheckboxes() {
        Set<BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        for (Map.Entry<BundlePlatform, JCheckBox> entry : platformCheckboxes.entrySet()) {
            BundlePlatform platform = entry.getKey();
            if (platform != BundlePlatform.All && platform != BundlePlatform.Default) {
                entry.getValue().setSelected(enabledPlatforms.contains(platform));
            }
        }
    }
    
    public void addChangeListener(ChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }
    
    public void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }
    
    private void fireChangeEvent() {
        if (!changeListeners.isEmpty()) {
            ChangeEvent event = new ChangeEvent(this);
            for (ChangeListener listener : changeListeners) {
                listener.stateChanged(event);
            }
        }
    }
    
    private class PlatformCheckboxListener implements ActionListener {
        private final BundlePlatform platform;
        
        public PlatformCheckboxListener(BundlePlatform platform) {
            this.platform = platform;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            JCheckBox checkbox = (JCheckBox) e.getSource();
            Set<BundlePlatform> enabledPlatforms = new HashSet<>(settings.getEnabledPlatforms());
            
            if (checkbox.isSelected()) {
                enabledPlatforms.add(platform);
            } else {
                enabledPlatforms.remove(platform);
            }
            
            settings.setEnabledPlatforms(enabledPlatforms);
            fireChangeEvent();
        }
    }
    
    private class RadioButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Set<BundlePlatform> enabledPlatforms = new HashSet<>();
            
            if (allRadioButton.isSelected()) {
                enabledPlatforms.add(BundlePlatform.All);
                customPlatformPanel.setVisible(false);
            } else if (defaultRadioButton.isSelected()) {
                enabledPlatforms.add(BundlePlatform.Default);
                customPlatformPanel.setVisible(false);
            } else if (customRadioButton.isSelected()) {
                customPlatformPanel.setVisible(true);
                for (Map.Entry<BundlePlatform, JCheckBox> entry : platformCheckboxes.entrySet()) {
                    BundlePlatform platform = entry.getKey();
                    if (platform != BundlePlatform.All && platform != BundlePlatform.Default && entry.getValue().isSelected()) {
                        enabledPlatforms.add(platform);
                    }
                }
            }
            
            settings.setEnabledPlatforms(enabledPlatforms);
            revalidate();
            repaint();
            fireChangeEvent();
        }
    }
}
