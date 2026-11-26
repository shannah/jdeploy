package com.joshondesign.appbundler.mac;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Embeds a project's provisioning profile in a macOS bundle before it is signed, and refuses to sign a bundle whose
 * entitlements macOS would only honour with one.
 *
 * <p>A project provides the profile as {@code jdeploy.mac.bundle.provisionprofile} in the working directory, beside
 * the {@code jdeploy.mac.bundle.entitlements} override. It is copied to {@code Contents/embedded.provisionprofile}.
 *
 * <p>Restricted entitlements ({@code com.apple.application-identifier}, {@code com.apple.developer.*},
 * {@code keychain-access-groups}) must be authorised by an embedded profile: macOS kills a process whose signature
 * claims one without it, at launch. So signing such a bundle without a profile is an error here, not at the user's
 * first launch. The CLI launcher ({@code Contents/MacOS/Client4JLauncher-cli}) is signed by {@code --deep} with the same
 * entitlements but is not the bundle's main executable, so the profile does not cover it; a bundle with one may not
 * claim restricted entitlements either.
 */
public final class MacProvisioningProfile {

    /** The project-local profile, read from the working directory. */
    public static final String OVERRIDE_FILE = "jdeploy.mac.bundle.provisionprofile";

    /** Where macOS looks for a bundle's profile, relative to {@code Contents}. */
    public static final String EMBEDDED_NAME = "embedded.provisionprofile";

    private MacProvisioningProfile() {}

    /**
     * Embeds {@code workingDir/jdeploy.mac.bundle.provisionprofile}, if present, in {@code contentsDir}, and checks that
     * the entitlements {@code entitlementsFile} claims can be honoured.
     *
     * @throws IOException when the profile can't be copied, or the entitlements claim a restricted entitlement that
     *                     this bundle can't have authorised
     */
    public static void prepare(File contentsDir, File entitlementsFile, File workingDir) throws IOException {
        File profile = new File(workingDir, OVERRIDE_FILE);
        if (profile.isFile()) {
            System.out.println("Embedding provisioning profile " + profile.getAbsolutePath());
            FileUtils.copyFile(profile, new File(contentsDir, EMBEDDED_NAME));
        }
        List<String> restricted = restrictedEntitlements(entitlementsFile);
        if (restricted.isEmpty()) return;
        if (!profile.isFile()) {
            throw new IOException("The entitlements in " + entitlementsFile.getName() + " claim " + restricted
                    + ", which macOS only allows with a provisioning profile. Add the profile as " + OVERRIDE_FILE
                    + " in " + workingDir.getAbsolutePath() + ", or remove those entitlements: signed without one, "
                    + "the app would be killed at launch.");
        }
        if (new File(contentsDir, "MacOS/" + CliInstallerConstants.CLI_LAUNCHER_NAME).exists()) {
            throw new IOException("The entitlements claim " + restricted + ", but this bundle carries the CLI launcher "
                    + CliInstallerConstants.CLI_LAUNCHER_NAME + ", which the provisioning profile doesn't cover. "
                    + "Restricted entitlements can't be used with jdeploy.commands on macOS yet.");
        }
    }

    /** The restricted entitlement keys {@code entitlementsFile} claims, in file order. */
    public static List<String> restrictedEntitlements(File entitlementsFile) throws IOException {
        List<String> out = new ArrayList<>();
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            doc = b.parse(entitlementsFile);
        } catch (Exception e) {
            throw new IOException("Couldn't read the entitlements in " + entitlementsFile.getAbsolutePath(), e);
        }
        NodeList dicts = doc.getElementsByTagName("dict");
        if (dicts.getLength() == 0) return out;
        Node dict = dicts.item(0);
        for (Node n = dict.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && "key".equals(((Element) n).getTagName())) {
                String key = n.getTextContent().trim();
                if (isRestricted(key)) out.add(key);
            }
        }
        return out;
    }

    static boolean isRestricted(String key) {
        return key.equals("com.apple.application-identifier")
                || key.startsWith("com.apple.developer.")
                || key.equals("keychain-access-groups");
    }
}
