/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security.net;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

/**
 *
 * @author shannah
 */
public class PermissionClient {
    private String key;
    private int port;
    private Socket sock;
    private InputStream input;
    private OutputStream output;
    private DataOutputStream doutput;
    private DataInputStream dinput;
    
    private static PermissionClient instance;
    public synchronized static PermissionClient getInstance() throws IOException {
        if (instance == null) {
            instance = new PermissionClient();
            instance.connect();
        }
        return instance;
    }
    
    public PermissionClient() {
        key = System.getProperty("client4j.pserver.key");
        port = Integer.parseInt(System.getProperty("client4j.pserver.port"));
    }

    public synchronized void connect() throws IOException {
        if (sock != null) {
            if (!sock.isClosed()) {
                throw new IllegalStateException("Already connected");
            }
            sock = null;
        }
        sock = new Socket("localhost", port);
        
        input = sock.getInputStream();
        output = sock.getOutputStream();
        dinput = new DataInputStream(input);
        doutput = new DataOutputStream(output);
        
        doutput.writeUTF(key);
        String response = dinput.readUTF();
        if (!Objects.equals(response, "CONNECTED")) {
            throw new IOException("Failed to connect to permission server: "+response);
        }
        
    }
    
    public synchronized PermissionResponse requestPermission(PermissionRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeUnshared(request);
        }
        doutput.writeInt(baos.size());
        doutput.write(baos.toByteArray());
        
        int len = dinput.readInt();
        byte[] buf = new byte[len];
        baos = new ByteArrayOutputStream();
        int off=0;
        while (baos.size() < len) {
            int num = dinput.read(buf, off, len-off);
            
            baos.write(buf, off, num);
            off += num;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))){
            return (PermissionResponse)ois.readUnshared();
        } catch (ClassNotFoundException ex) {
            throw new IOException(ex);
        }
    }
    
    
    
}
