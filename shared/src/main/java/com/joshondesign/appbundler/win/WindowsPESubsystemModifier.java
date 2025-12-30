package com.joshondesign.appbundler.win;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for modifying Windows PE executable headers.
 * 
 * Specifically, this class can modify the subsystem field in the PE header
 * to change an executable from GUI (2) to Console (3) subsystem.
 */
public class WindowsPESubsystemModifier {
    
    private static final String PE_SIGNATURE = "PE\0\0";
    private static final int PE_SIGNATURE_OFFSET_LOCATION = 0x3C;
    private static final int SUBSYSTEM_FIELD_OFFSET = 92; // PE sig (4) + COFF header (20) + offset in Optional Header (68)
    private static final byte SUBSYSTEM_CONSOLE = 3;
    private static final byte SUBSYSTEM_GUI = 2;
    
    /**
     * Copies an EXE file and modifies its PE header subsystem from GUI to Console.
     * 
     * @param sourceExe the source EXE file to read
     * @param destExe the destination EXE file to write
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the file is not a valid PE executable or cannot be modified
     */
    public static void copyAndModifySubsystem(File sourceExe, File destExe) 
            throws IOException, IllegalArgumentException {
        
        if (sourceExe == null || !sourceExe.exists()) {
            throw new IllegalArgumentException("Source EXE file does not exist: " + sourceExe);
        }
        
        if (!sourceExe.isFile()) {
            throw new IllegalArgumentException("Source EXE is not a regular file: " + sourceExe);
        }
        
        // First, copy the file
        Files.copy(sourceExe.toPath(), destExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        // Then modify the PE header
        modifyPEHeaderSubsystem(destExe);
    }
    
    /**
     * Modifies an existing EXE file's PE header subsystem from GUI to Console.
     * 
     * @param exeFile the EXE file to modify
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the file is not a valid PE executable
     */
    public static void modifyPEHeaderSubsystem(File exeFile) 
            throws IOException, IllegalArgumentException {
        
        if (exeFile == null || !exeFile.exists()) {
            throw new IllegalArgumentException("EXE file does not exist: " + exeFile);
        }
        
        if (!exeFile.isFile()) {
            throw new IllegalArgumentException("Target is not a regular file: " + exeFile);
        }
        
        byte[] fileContent = Files.readAllBytes(exeFile.toPath());
        
        // Validate that this is an MZ executable (DOS header)
        if (fileContent.length < 64) {
            throw new IllegalArgumentException("File is too small to be a valid PE executable");
        }
        
        if (fileContent[0] != 'M' || fileContent[1] != 'Z') {
            throw new IllegalArgumentException("File does not have a valid MZ executable header");
        }
        
        // Read the PE header offset from the DOS header
        int peHeaderOffset = readInt32LittleEndian(fileContent, PE_SIGNATURE_OFFSET_LOCATION);
        
        // Validate the PE header offset is within bounds
        if (peHeaderOffset < 0 || peHeaderOffset + 4 > fileContent.length) {
            throw new IllegalArgumentException("Invalid PE header offset: " + peHeaderOffset);
        }
        
        // Validate the PE signature
        if (!isPESignatureValid(fileContent, peHeaderOffset)) {
            throw new IllegalArgumentException("File does not have a valid PE signature at offset " + peHeaderOffset);
        }
        
        // Calculate the subsystem field offset within the PE header
        // The subsystem field is located 68 bytes after the PE signature
        int subsystemOffset = peHeaderOffset + SUBSYSTEM_FIELD_OFFSET;
        
        if (subsystemOffset >= fileContent.length) {
            throw new IllegalArgumentException("Subsystem field offset is out of bounds");
        }
        
        // Modify the subsystem field from GUI (2) to Console (3)
        fileContent[subsystemOffset] = SUBSYSTEM_CONSOLE;
        
        // Write the modified content back
        Files.write(exeFile.toPath(), fileContent);
    }
    
    /**
     * Checks if a PE signature is valid at the given offset.
     * 
     * @param fileContent the file content bytes
     * @param peHeaderOffset the offset where the PE signature should be
     * @return true if the PE signature is valid, false otherwise
     */
    private static boolean isPESignatureValid(byte[] fileContent, int peHeaderOffset) {
        if (peHeaderOffset + 4 > fileContent.length) {
            return false;
        }
        
        return fileContent[peHeaderOffset] == 'P' 
            && fileContent[peHeaderOffset + 1] == 'E'
            && fileContent[peHeaderOffset + 2] == 0
            && fileContent[peHeaderOffset + 3] == 0;
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
