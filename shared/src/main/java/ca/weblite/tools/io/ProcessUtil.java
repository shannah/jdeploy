/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class ProcessUtil {
    public static String execAndReturnString(File cwd, String... args) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(args).directory(cwd);
            Process p = pb.start();
            Scanner scanner = new Scanner(p.getInputStream(), "UTF-8");
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                
                sb.append(scanner.nextLine()).append("\n");
            }
            int result = p.waitFor();
            if (result != 0) {
                throw new IOException("Failed to execute command: "+Arrays.toString(args)+".  Exit code "+result);
            }
            return sb.toString();
        } catch (InterruptedException ex) {
            Logger.getLogger(ProcessUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }
}
