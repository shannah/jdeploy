/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.publisher.server;

import ca.weblite.tools.io.FileUtil;
import com.codename1.io.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author shannah
 */
public class SigningResponse implements Cleanable, Externalizable {

    public SigningResponse() {
        
    }
    private static final int VERSION=1;
    private int codesignExitCode, altoolExitCode, responseCode;
    private String codesignLog, altoolLog;
    private File file;
    private boolean ranCodesign, ranAltool;
    private String errorMessage, stackTrace;
    /**
     * @return the codesignExitCode
     */
    public int getCodesignExitCode() {
        return codesignExitCode;
    }

    /**
     * @param codesignExitCode the codesignExitCode to set
     */
    public void setCodesignExitCode(int codesignExitCode) {
        this.codesignExitCode = codesignExitCode;
    }

    /**
     * @return the altoolExitCode
     */
    public int getAltoolExitCode() {
        return altoolExitCode;
    }

    /**
     * @param altoolExitCode the altoolExitCode to set
     */
    public void setAltoolExitCode(int altoolExitCode) {
        this.altoolExitCode = altoolExitCode;
    }

    /**
     * @return the responseCode
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * @param responseCode the responseCode to set
     */
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    /**
     * @return the codesignLog
     */
    public String getCodesignLog() {
        return codesignLog;
    }

    /**
     * @param codesignLog the codesignLog to set
     */
    public void setCodesignLog(String codesignLog) {
        this.codesignLog = codesignLog;
    }

    /**
     * @return the altoolLog
     */
    public String getAltoolLog() {
        return altoolLog;
    }

    /**
     * @param altoolLog the altoolLog to set
     */
    public void setAltoolLog(String altoolLog) {
        this.altoolLog = altoolLog;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @param file the file to set
     */
    public void setFile(File file) {
        this.file = file;
    }
    
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(VERSION);
        out.writeBoolean(ranAltool);
        out.writeBoolean(ranCodesign);
        out.writeInt(responseCode);
        out.writeInt(codesignExitCode);
        out.writeInt(altoolExitCode);
        out.writeObject(codesignLog);
        out.writeObject(altoolLog);
        out.writeObject(errorMessage);
        out.writeObject(stackTrace);
        out.writeObject(file == null ? null : file.getName());
        out.writeLong(file == null ? 0 : file.length());
        if (file != null) {
            try (FileInputStream fis = new FileInputStream(file)) {
                int len = 0;
                byte[] buf = new byte[4096];
                while ((len = fis.read(buf)) > -1) {
                    if (len > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        ranAltool = in.readBoolean();
        ranCodesign = in.readBoolean();
        responseCode = in.readInt();
        codesignExitCode = in.readInt();
        altoolExitCode = in.readInt();
        codesignLog = (String)in.readObject();
        altoolLog = (String)in.readObject();
        errorMessage = (String)in.readObject();
        stackTrace = (String)in.readObject();
        String fileName = (String)in.readObject();
        long counter = in.readLong();
        if (fileName != null && counter > 0) {
            final File tmpFile = File.createTempFile("C4JSigningResponse", "dir");
            tmpFile.delete();
            tmpFile.mkdir();
            cleanupTasks.add(()->{
                try {
                    if (tmpFile.exists()) {
                        FileUtil.delTree(tmpFile);
                    }
                } catch (Throwable ex) {
                    Log.e(ex);
                }
            });

            file = new File(tmpFile, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buf = new byte[4096];
                int len = 0;
                while (counter > 0 && (len = in.read(buf, 0, (int)Math.min(buf.length, counter))) >= 0) {
                    if (len > 0) {
                        fos.write(buf, 0, len);
                        counter -= len;
                    }
                }
            }
        }
    }
    
    private List<Runnable> cleanupTasks = new ArrayList<>();
    public void addCleanupTask(Runnable r) {
        cleanupTasks.add(r);
    }
    
    /**
     * @return the ranCodesign
     */
    public boolean isRanCodesign() {
        return ranCodesign;
    }

    /**
     * @param ranCodesign the ranCodesign to set
     */
    public void setRanCodesign(boolean ranCodesign) {
        this.ranCodesign = ranCodesign;
    }

    /**
     * @return the ranAltool
     */
    public boolean isRanAltool() {
        return ranAltool;
    }

    /**
     * @param ranAltool the ranAltool to set
     */
    public void setRanAltool(boolean ranAltool) {
        this.ranAltool = ranAltool;
    }
    
    public void cleanup() {
        while (!cleanupTasks.isEmpty()) {
            cleanupTasks.remove(0).run();
        }
    }

    public void setStackTrace(String trace) {
        stackTrace = trace;
    }

    public void setErrorMessage(String message) {
        errorMessage = message;
    }
    
}


