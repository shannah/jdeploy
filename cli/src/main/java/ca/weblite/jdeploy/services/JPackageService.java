package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.helpers.FileAssociationsHelper;
import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.platform.Platform;
import com.github.gino0631.icns.IcnsBuilder;
import com.github.gino0631.icns.IcnsType;
import com.joshondesign.appbundler.mac.MacBundler;
import net.coobird.thumbnailator.Thumbnails;
import net.sf.image4j.codec.ico.ICOEncoder;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

public class JPackageService extends BaseService {
    private String jpackagePath="jpackage";

    public JPackageService(File packageJSONFile, JSONObject packageJSON) throws IOException {
        super(packageJSONFile, packageJSON);
        if (System.getenv("JDEPLOY_JPACKAGE") != null) {
            jpackagePath = System.getenv("JDEPLOY_JPACKAGE");
        } else {
            jpackagePath = findJPackage();
        }

    }

    public void execute() throws Exception {
        run();
    }

    private void run() throws Exception {
        Set<String> usedArgs = new HashSet<>();
        ProcessBuilder pb = new ProcessBuilder(jpackagePath,
                "--input", getArg("--input", getJDeployBundleDirectory().getAbsolutePath()),
                "--main-jar", getArg("--main-jar", getMainJarFile().getName()),
                "--dest", getArg("--dest", getDestDirectory().getAbsolutePath()),
                "--description", getArg("--description", getDescription()),
                "--app-version", getArg("--app-version", getAppVersion()),
                "--name", getAppName());
        Set<String> usedFlags = new HashSet<>();
        if (args.containsKey("--type") && "msi".equals(args.get("--type")) ) {
            pb.command().add("--win-menu");
            usedFlags.add("--win-menu");
        }
        usedArgs.add("--input");
        usedArgs.add("--main-jar");
        usedArgs.add("--dest");
        usedArgs.add("--description");
        usedArgs.add("--name");
        if (getIconFile().exists()) {
            File icnsFile = processIcon();
            pb.command().add("--icon");
            pb.command().add(getArg("--icon", icnsFile.getAbsolutePath()));
        } else if (args.containsKey("--icon")){
            pb.command().add("--icon");
            pb.command().add(args.get("--icon"));
        }
        usedArgs.add("--icon");
        appendFileAssociationsToCommand(pb.command());
        for (String argKey : args.keySet()) {
            if (!usedArgs.contains(argKey)) {
                usedArgs.add(argKey);
                pb.command().add(argKey);
                pb.command().add(args.get(argKey));
            }
        }
        for (String flag : flags) {
            if (!usedFlags.contains(flag)) {
                pb.command().add(flag);
                usedFlags.add(flag);
            }
        }
        Process p = pb.start();
        int result;
        if ((result = p.waitFor()) != 0) {
            throw new IOException("jpackage process failed result code "+result);
        }
    }

    protected File getDestDirectory() throws IOException {
        File dest = new File(super.getDestDirectory(),  "jpackage");
        dest.mkdirs();
        return dest;
    }

    private File createTempFileAssociationPropertiesFile(DocumentTypeAssociation fileType) throws IOException {
        File temp = File.createTempFile(fileType.getExtension(), ".properties");
        temp.deleteOnExit();
        try (PrintStream printStream = new PrintStream(new FileOutputStream(temp))) {
            printStream.println("mime-type="+fileType.getMimetype());
            printStream.println("extension="+fileType.getExtension());
            printStream.println("description="+fileType.getExtension()+" file");
        }
        return temp;
    }

    private void appendFileAssociatonToCommand(List<String> command, DocumentTypeAssociation assoc) throws IOException {
        command.add("--file-associations");
        command.add(createTempFileAssociationPropertiesFile(assoc).getAbsolutePath());
    }

    private void appendFileAssociationsToCommand(List<String> command, Iterable<DocumentTypeAssociation> associations) throws IOException {
        for (DocumentTypeAssociation assoc : associations) {
            appendFileAssociatonToCommand(command, assoc);
        }

    }

    private void appendFileAssociationsToCommand(List<String> command, JSONObject packageJSON) throws IOException {
        appendFileAssociationsToCommand(command, FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON));
    }

    private void appendFileAssociationsToCommand(List<String> command) throws IOException {
        appendFileAssociationsToCommand(command, packageJSON);
    }

    private String findJPackage() {
        for (File dir : getEnvironmentPath()) {
            File jpackage = new File(dir,"jpackage");
            if (jpackage.exists()) {
                return jpackage.getAbsolutePath();
            }
            jpackage = new File(dir, "jpackage.exe");
            if (jpackage.exists()) {
                return jpackage.getAbsolutePath();
            }
        }
        return "jpackage";
    }

    private File processIcon() throws Exception {
        if (Platform.getSystemPlatform().isMac()) {
            return processMacIcon();
        } else if (Platform.getSystemPlatform().isWindows()) {
            return processWindowsIcon();
        } else {
            return getIconFile();
        }
    }

    private File processWindowsIcon() throws IOException {
        File srcPng = getIconFile();
        File destIco = new File(getJDeployBundleDirectory(), "icon.ico");
        List<BufferedImage> images = new ArrayList<>();
        List<Integer> bppList = new ArrayList<>();
        for (int i : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            BufferedImage img = Thumbnails.of(srcPng).size(i, i).asBufferedImage();
            images.add(img);
            bppList.add(32);
            if (i <= 48) {
                images.add(img);
                bppList.add(8);
                images.add(img);
                bppList.add(4);
            }
        }
        int[] bppArray = bppList.stream().mapToInt(i->i).toArray();
        try (FileOutputStream fileOutputStream = new FileOutputStream(destIco)) {
            ICOEncoder.write(images,bppArray, fileOutputStream);
        }
        return destIco;

    }

    private File processMacIcon() throws Exception {

        File iconFile = File.createTempFile("icon", ".png");
        iconFile.deleteOnExit();
        FileUtils.copyFile(getIconFile(), iconFile);

        Icon icn = new Icon();
        icn.set(iconFile.getAbsolutePath());

        String osType = null;
        try {
            osType = getOsType(icn);
        } catch (Throwable t){
            t.printStackTrace(System.err);
        }
        if (osType == null) {
            Thumbnails.of(iconFile).outputFormat("png").size(512, 512).toFile(iconFile);
            try {
                osType = getOsType(icn);
            } catch (Throwable t){
                t.printStackTrace(System.err);
            }
        }

        if (osType == null) {
            throw new IOException("Failed to determine type of icon file.");
        }

        try (IcnsBuilder builder = IcnsBuilder.getInstance()) {

            builder.add(osType, new FileInputStream(iconFile));

            File icnsFile = new File(getJDeployBundleDirectory(), "icon.icns");
            try (FileOutputStream out = new FileOutputStream(icnsFile)) {
                builder.build().writeTo(out);
            }
            return icnsFile;

        } finally {
            iconFile.delete();
        }
    }


    private static class Icon {
        /**
         * Icon file.
         */

        private String file;

        /**
         * OSType identifier of the icon type.
         */

        private String osType;

        public Icon set(String file) {
            this.file = file;

            return this;
        }

        public String getFile() {
            return file;
        }

        public String getOsType() {
            return osType;
        }
    }

    private static String getOsType(Icon icon) throws IllegalArgumentException {
        String osType = icon.getOsType();

        if (osType != null) {
            return osType;

        } else {
            String name = Paths.get(icon.getFile()).getFileName().toString();

            try {
                try (InputStream is = getInputStream(icon)) {
                    try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
                        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

                        if (imageReaders.hasNext()) {
                            ImageReader reader = imageReaders.next();

                            try {
                                String format = reader.getFormatName();
                                if (format.equals("png") || format.equals("jpeg")) {
                                    reader.setInput(iis, true, true);
                                    BufferedImage img = reader.read(0, reader.getDefaultReadParam());

                                    if (img.getWidth() == img.getHeight()) {
                                        switch (img.getWidth()) {
                                            case 16:
                                                return IcnsType.ICNS_16x16_JPEG_PNG_IMAGE.getOsType();

                                            case 32:
                                                return IcnsType.ICNS_32x32_JPEG_PNG_IMAGE.getOsType();

                                            case 64:
                                                return IcnsType.ICNS_64x64_JPEG_PNG_IMAGE.getOsType();

                                            case 128:
                                                return IcnsType.ICNS_128x128_JPEG_PNG_IMAGE.getOsType();

                                            case 256:
                                                return IcnsType.ICNS_256x256_JPEG_PNG_IMAGE.getOsType();

                                            case 512:
                                                return IcnsType.ICNS_512x512_JPEG_PNG_IMAGE.getOsType();

                                            case 1024:
                                                return IcnsType.ICNS_1024x1024_2X_JPEG_PNG_IMAGE.getOsType();
                                        }
                                    }

                                    throw new IllegalArgumentException(MessageFormat.format("Size of {0} icon is not supported", name));
                                }

                            } finally {
                                reader.dispose();
                            }
                        }

                        throw new IllegalArgumentException(MessageFormat.format("Unable to determine type of {0} icon", name));
                    }
                }

            } catch (IOException e) {
                throw new IllegalArgumentException("Error determining icon type", e);
            }
        }
    }

    private static InputStream getInputStream(Icon icon) throws IOException {
        return new FileInputStream(new File(icon.getFile()));
    }

    public boolean doesJdkIncludeJavaFX() {
        File jpackageFile = new File(jpackagePath);
        if (!jpackageFile.exists()) return false;
        File binDir = jpackageFile.getParentFile();
        File modsDir = new File(binDir.getParentFile(), "mods");
        if (modsDir.exists()) {
            for (File f : modsDir.listFiles()) {
                if (f.getName().startsWith("javafx.")) {
                    return true;
                }
            }
        }
        return false;
    }




}
