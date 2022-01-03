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
public class SigningRequest implements Cleanable, Externalizable {

    /**
     * @return the bundleId
     */
    public String getBundleId() {
        return bundleId;
    }

    /**
     * @param bundleId the bundleId to set
     */
    public void setBundleId(String bundleId) {
        this.bundleId = bundleId;
    }

    private static final int VERSION=1;
    private String developerId, certificateName, notarizationPassword, bundleId;
    private boolean notarize, codesign;
    private File file;
    
    private List<Runnable> cleanupTasks = new ArrayList<>();
    
    public SigningRequest(){}
    
    public SigningRequest(String developerId, String certificateName, String notarizationPassword) {
        this.developerId = developerId;
        this.certificateName = certificateName;
        this.notarizationPassword = notarizationPassword;
    }
    
    /**
     * @return the developerId
     */
    public String getDeveloperId() {
        return developerId;
    }

    /**
     * @param developerId the developerId to set
     */
    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
    }

    /**
     * @return the certificateName
     */
    public String getCertificateName() {
        return certificateName;
    }

    /**
     * @param certificateName the certificateName to set
     */
    public void setCertificateName(String certificateName) {
        this.certificateName = certificateName;
    }

    /**
     * @return the notarizationPassword
     */
    public String getNotarizationPassword() {
        return notarizationPassword;
    }

    /**
     * @param notarizationPassword the notarizationPassword to set
     */
    public void setNotarizationPassword(String notarizationPassword) {
        this.notarizationPassword = notarizationPassword;
    }

    /**
     * @return the notarize
     */
    public boolean isNotarize() {
        return notarize;
    }

    /**
     * @param notarize the notarize to set
     */
    public void setNotarize(boolean notarize) {
        this.notarize = notarize;
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
        out.writeObject(bundleId);
        out.writeObject(developerId);
        out.writeObject(certificateName);
        out.writeObject(notarizationPassword);
        out.writeBoolean(notarize);
        out.writeBoolean(isCodesign());
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
        bundleId = (String)in.readObject();
        developerId = (String)in.readObject();
        certificateName = (String)in.readObject();
        notarizationPassword = (String)in.readObject();
        notarize = in.readBoolean();
        setCodesign(in.readBoolean());
        String fileName = (String)in.readObject();
        long counter = in.readLong();
        if (fileName != null && counter > 0) {
            final File tmpFile = File.createTempFile("C4JSigningRequest", "dir");
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
    
    public void cleanup() {
        while (!cleanupTasks.isEmpty()) {
            cleanupTasks.remove(0).run();
        }
    }

    /**
     * @return the codesign
     */
    public boolean isCodesign() {
        return codesign;
    }

    /**
     * @param codesign the codesign to set
     */
    public void setCodesign(boolean codesign) {
        this.codesign = codesign;
    }
    
}
