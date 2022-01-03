/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/**
 *
 * @author shannah
 */
public class FileUtil {

    private static final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    public static boolean isPosix() {
        return isPosix;
    }

    public static File createTempDirectory(String prefix, String suffix, File parentDirectory) throws IOException {
        final File tmp = File.createTempFile(prefix, suffix, parentDirectory);
        tmp.delete();
        tmp.mkdir();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (tmp.exists()) {
                delTree(tmp);
            }
        }));
        return tmp;

    }

    public static boolean delTree(File directory) {
        if (directory.isDirectory()) {
            for (File child : directory.listFiles()) {
                delTree(child);
            }
        }
        return directory.delete();
    }

    public static void writeStringToFile(String string, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(file))) {
            writer.append(string);
        }
    }

    public static String readFileToString(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return IOUtil.readToString(fis);
        }
    }

    public static long readFileToLong(File file) throws IOException {
        return Long.parseLong(readFileToString(file).trim());
    }

    public static List<File> find(File root, String prefix, String suffix) {
        return find(new ArrayList<File>(), root, prefix, suffix);
    }

    private static List<File> find(List<File> out, File root, String prefix, String suffix) {
        String name = root.getName();
        boolean match = true;
        if (prefix != null && !name.startsWith(prefix)) {
            match = false;
        }
        if (suffix != null && !name.endsWith(suffix)) {
            match = false;
        }
        if (match) {
            out.add(root);
        }
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                find(out, child, prefix, suffix);
            }
        }
        return out;
    }

    public static void copy(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            copyDirectory(src.toPath(), dest.toPath());
        } else {
            try (FileInputStream fis = new FileInputStream(src)) {
                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    IOUtil.copy(fis, fos);
                }
            }
        }
    }

    /**
     * Copies a directory.
     * <p>
     * NOTE: This method is not thread-safe.
     * <p>
     *
     * @param source the directory to copy from
     * @param target the directory to copy into
     * @throws IOException if an I/O error occurs
     */
    private static void copyDirectory(final Path source, final Path target)
            throws IOException {
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes sourceBasic) throws IOException {
                Path targetDir = Files.createDirectories(target
                        .resolve(source.relativize(dir)));
                AclFileAttributeView acl = Files.getFileAttributeView(dir,
                        AclFileAttributeView.class);
                if (acl != null) {
                    Files.getFileAttributeView(targetDir,
                            AclFileAttributeView.class).setAcl(acl.getAcl());
                }
                DosFileAttributeView dosAttrs = Files.getFileAttributeView(
                        dir, DosFileAttributeView.class);
                if (dosAttrs != null) {
                    DosFileAttributes sourceDosAttrs = dosAttrs
                            .readAttributes();
                    DosFileAttributeView targetDosAttrs = Files
                            .getFileAttributeView(targetDir,
                                    DosFileAttributeView.class);
                    targetDosAttrs.setArchive(sourceDosAttrs.isArchive());
                    targetDosAttrs.setHidden(sourceDosAttrs.isHidden());
                    targetDosAttrs.setReadOnly(sourceDosAttrs.isReadOnly());
                    targetDosAttrs.setSystem(sourceDosAttrs.isSystem());
                }
                FileOwnerAttributeView ownerAttrs = Files
                        .getFileAttributeView(dir, FileOwnerAttributeView.class);
                if (ownerAttrs != null) {
                    FileOwnerAttributeView targetOwner = Files
                            .getFileAttributeView(targetDir,
                                    FileOwnerAttributeView.class);
                    targetOwner.setOwner(ownerAttrs.getOwner());
                }
                PosixFileAttributeView posixAttrs = Files
                        .getFileAttributeView(dir, PosixFileAttributeView.class);
                if (posixAttrs != null) {
                    PosixFileAttributes sourcePosix = posixAttrs
                            .readAttributes();
                    PosixFileAttributeView targetPosix = Files
                            .getFileAttributeView(targetDir,
                                    PosixFileAttributeView.class);
                    targetPosix.setPermissions(sourcePosix.permissions());
                    targetPosix.setGroup(sourcePosix.group());
                }
                UserDefinedFileAttributeView userAttrs = Files
                        .getFileAttributeView(dir,
                                UserDefinedFileAttributeView.class);
                if (userAttrs != null) {
                    UserDefinedFileAttributeView targetUser = Files
                            .getFileAttributeView(targetDir,
                                    UserDefinedFileAttributeView.class);
                    for (String key : userAttrs.list()) {
                        ByteBuffer buffer = ByteBuffer.allocate(userAttrs
                                .size(key));
                        userAttrs.read(key, buffer);
                        buffer.flip();
                        targetUser.write(key, buffer);
                    }
                }
                // Must be done last, otherwise last-modified time may be
                // wrong
                BasicFileAttributeView targetBasic = Files
                        .getFileAttributeView(targetDir,
                                BasicFileAttributeView.class);
                targetBasic.setTimes(sourceBasic.lastModifiedTime(),
                        sourceBasic.lastAccessTime(),
                        sourceBasic.creationTime());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult
                    visitFileFailed(Path file, IOException e)
                    throws IOException {
                throw e;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                    IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }

        });
    }

    private static Map<Integer, PosixFilePermission> intToPosixFilePermission = new HashMap<>();

    static {
        intToPosixFilePermission.put(0400, PosixFilePermission.OWNER_READ);
        intToPosixFilePermission.put(0200, PosixFilePermission.OWNER_WRITE);
        intToPosixFilePermission.put(0100, PosixFilePermission.OWNER_EXECUTE);

        intToPosixFilePermission.put(0040, PosixFilePermission.GROUP_READ);
        intToPosixFilePermission.put(0020, PosixFilePermission.GROUP_WRITE);
        intToPosixFilePermission.put(0010, PosixFilePermission.GROUP_EXECUTE);

        intToPosixFilePermission.put(0004, PosixFilePermission.OTHERS_READ);
        intToPosixFilePermission.put(0002, PosixFilePermission.OTHERS_WRITE);
        intToPosixFilePermission.put(0001, PosixFilePermission.OTHERS_EXECUTE);
    }

    public static Set<PosixFilePermission> getPosixFilePermissions(int mode) {
        Set<PosixFilePermission> permissionSet = new HashSet<>();
        for (Map.Entry<Integer, PosixFilePermission> entry : intToPosixFilePermission.entrySet()) {
            if ((mode & entry.getKey()) > 0) {
                permissionSet.add(entry.getValue());
            }
        }
        return permissionSet;
    }

    public static int getPosixFilePermissions(File file) throws IOException {
        return getPosixFilePermissions(Files.getPosixFilePermissions(file.toPath()));
    }

    public static int getPosixFilePermissions(Set<PosixFilePermission> perms) {
        int mode = 0;
        for (int iPerm : intToPosixFilePermission.keySet()) {
            PosixFilePermission perm = intToPosixFilePermission.get(iPerm);
            if (perms.contains(perm)) {
                mode |= iPerm;
            }
        }
        return mode;
    }

    public static void setPosixPermissions(int mode, File file) {
        try {
            Set<PosixFilePermission> posixFilePermissions = getPosixFilePermissions(mode);
            Files.setPosixFilePermissions(file.toPath(), posixFilePermissions);
        } catch (Exception e) {
            System.err.println("Could not set file permissions of " + file + ". Exception was: " + e.getMessage());
        }
    }

}
