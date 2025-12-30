package com.joshondesign.appbundler.win;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WindowsPESubsystemModifier.
 */
@DisplayName("WindowsPESubsystemModifier Tests")
class WindowsPESubsystemModifierTest {
    
    private Path tempDir;
    
    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("pe-modifier-test");
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp directory
        Files.walk(tempDir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            });
    }
    
    @Test
    @DisplayName("Should copy and modify PE header subsystem from GUI to Console")
    void testCopyAndModifySubsystem() throws IOException {
        File sourceExe = createTestPEExecutable(tempDir.resolve("source.exe").toFile(), true);
        File destExe = tempDir.resolve("dest.exe").toFile();
        
        // Act
        WindowsPESubsystemModifier.copyAndModifySubsystem(sourceExe, destExe);
        
        // Assert
        assertTrue(destExe.exists(), "Destination file should exist");
        assertTrue(destExe.length() > 0, "Destination file should not be empty");
        
        byte[] destContent = Files.readAllBytes(destExe.toPath());
        int peOffset = readInt32LittleEndian(destContent, 0x3C);
        int subsystemOffset = peOffset + 68;
        
        assertEquals(3, destContent[subsystemOffset], 
            "Subsystem field should be modified to Console (3)");
    }
    
    @Test
    @DisplayName("Should validate PE signature before modification")
    void testValidatePESignature() throws IOException {
        File invalidExe = tempDir.resolve("invalid.exe").toFile();
        createInvalidPEExecutable(invalidExe);
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> WindowsPESubsystemModifier.modifyPEHeaderSubsystem(invalidExe),
            "Should throw exception for invalid PE signature");
    }
    
    @Test
    @DisplayName("Should reject non-existent source file")
    void testNonExistentSourceFile() {
        File nonExistent = new File("/nonexistent/path/file.exe");
        File dest = tempDir.resolve("dest.exe").toFile();
        
        assertThrows(IllegalArgumentException.class,
            () -> WindowsPESubsystemModifier.copyAndModifySubsystem(nonExistent, dest),
            "Should throw exception for non-existent source file");
    }
    
    @Test
    @DisplayName("Should reject non-existent target file for direct modification")
    void testNonExistentTargetFile() {
        File nonExistent = new File("/nonexistent/path/file.exe");
        
        assertThrows(IllegalArgumentException.class,
            () -> WindowsPESubsystemModifier.modifyPEHeaderSubsystem(nonExistent),
            "Should throw exception for non-existent target file");
    }
    
    @Test
    @DisplayName("Should reject files that are too small to be valid PE executables")
    void testFileTooSmall() throws IOException {
        File tinyFile = tempDir.resolve("tiny.exe").toFile();
        try (FileOutputStream fos = new FileOutputStream(tinyFile)) {
            fos.write(new byte[]{'M', 'Z'});
        }
        
        assertThrows(IllegalArgumentException.class,
            () -> WindowsPESubsystemModifier.modifyPEHeaderSubsystem(tinyFile),
            "Should throw exception for file that is too small");
    }
    
    @Test
    @DisplayName("Should reject files without MZ header")
    void testInvalidMZHeader() throws IOException {
        File invalidFile = tempDir.resolve("invalid.exe").toFile();
        byte[] content = new byte[100];
        content[0] = 'X';
        content[1] = 'Y';
        Files.write(invalidFile.toPath(), content);
        
        assertThrows(IllegalArgumentException.class,
            () -> WindowsPESubsystemModifier.modifyPEHeaderSubsystem(invalidFile),
            "Should throw exception for missing MZ header");
    }
    
    @Test
    @DisplayName("Should handle PE header offset out of bounds")
    void testPEHeaderOffsetOutOfBounds() throws IOException {
        File exeFile = tempDir.resolve("badoffset.exe").toFile();
        byte[] content = new byte[100];
        content[0] = 'M';
        content[1] = 'Z';
        // Set PE header offset to an invalid location
        content[0x3C] = (byte) 0xFF;
        content[0x3D] = (byte) 0xFF;
        content[0x3E] = (byte) 0xFF;
        content[0x3F] = (byte) 0xFF;
        Files.write(exeFile.toPath(), content);
        
        assertThrows(IllegalArgumentException.class,
            () -> WindowsPESubsystemModifier.modifyPEHeaderSubsystem(exeFile),
            "Should throw exception for invalid PE header offset");
    }
    
    @Test
    @DisplayName("Should preserve file content except for subsystem field")
    void testFileContentPreservation() throws IOException {
        File sourceExe = createTestPEExecutable(tempDir.resolve("source.exe").toFile(), true);
        File destExe = tempDir.resolve("dest.exe").toFile();
        
        byte[] sourceContent = Files.readAllBytes(sourceExe.toPath());
        
        // Act
        WindowsPESubsystemModifier.copyAndModifySubsystem(sourceExe, destExe);
        
        // Assert
        byte[] destContent = Files.readAllBytes(destExe.toPath());
        assertEquals(sourceContent.length, destContent.length, 
            "File sizes should be equal");
        
        int peOffset = readInt32LittleEndian(destContent, 0x3C);
        int subsystemOffset = peOffset + 68;
        
        // Verify all bytes except subsystem field are identical
        for (int i = 0; i < destContent.length; i++) {
            if (i != subsystemOffset) {
                assertEquals(sourceContent[i], destContent[i], 
                    "Byte at position " + i + " should be preserved");
            }
        }
    }
    
    // Helper methods
    
    /**
     * Creates a minimal but valid PE executable for testing.
     * 
     * @param file the file to create
     * @param setGUISubsystem if true, sets the subsystem to GUI (2), otherwise Console (3)
     * @return the created file
     * @throws IOException if an I/O error occurs
     */
    private static File createTestPEExecutable(File file, boolean setGUISubsystem) throws IOException {
        byte[] content = new byte[1024];
        
        // MZ header
        content[0] = 'M';
        content[1] = 'Z';
        
        // PE header offset at 0x3C (set to 0x80 = 128)
        content[0x3C] = (byte) 0x80;
        content[0x3D] = (byte) 0x00;
        content[0x3E] = (byte) 0x00;
        content[0x3F] = (byte) 0x00;
        
        // PE signature at offset 0x80
        int peOffset = 0x80;
        content[peOffset] = 'P';
        content[peOffset + 1] = 'E';
        content[peOffset + 2] = 0;
        content[peOffset + 3] = 0;
        
        // Subsystem field at PE signature + 68
        int subsystemOffset = peOffset + 68;
        content[subsystemOffset] = setGUISubsystem ? (byte) 2 : (byte) 3;
        
        Files.write(file.toPath(), content);
        return file;
    }
    
    /**
     * Creates an invalid PE executable (with bad PE signature).
     * 
     * @param file the file to create
     * @throws IOException if an I/O error occurs
     */
    private static void createInvalidPEExecutable(File file) throws IOException {
        byte[] content = new byte[500];
        
        // MZ header
        content[0] = 'M';
        content[1] = 'Z';
        
        // PE header offset at 0x3C (set to 0x80)
        content[0x3C] = (byte) 0x80;
        content[0x3D] = (byte) 0x00;
        content[0x3E] = (byte) 0x00;
        content[0x3F] = (byte) 0x00;
        
        // Invalid PE signature at offset 0x80 (should be "PE\0\0")
        int peOffset = 0x80;
        content[peOffset] = 'X';
        content[peOffset + 1] = 'Y';
        content[peOffset + 2] = 0;
        content[peOffset + 3] = 0;
        
        Files.write(file.toPath(), content);
    }
    
    /**
     * Reads a 32-bit little-endian integer from the given offset.
     * 
     * @param data the byte array
     * @param offset the offset to read from
     * @return the integer value
     */
    private static int readInt32LittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }
}
