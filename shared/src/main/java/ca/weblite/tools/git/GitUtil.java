/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.git;

import ca.weblite.tools.io.ProcessUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Scanner;

/**
 *
 * @author shannah
 */
public class GitUtil {
    private File directory;
    private String GIT = "git";
    public static final String GIT_PATH_PROPERTY = "c4j.git";
    
    public GitUtil(File directory) {
        
        this.directory = directory == null ? new File(".") : directory;
        if (!this.directory.isDirectory()) {
            this.directory = this.directory.getParentFile();
        }
        GIT = System.getProperty(GIT_PATH_PROPERTY, GIT);
        
    }
    
    public void printGitInstructions(PrintStream out) {
        
        out.println("Could not find git at "
                +GIT+
                ".  To enable git integration, ensure that either git is in your path "
                        + "or specify the path to git using -Dc4j.git=path/to/git"
        );
        
        
    }
    
    public void setGitPath(String path) {
        GIT = path;
    }
    
    public String getGitPath(String path) {
        return GIT;
    }
    
    
    public String checkGitVersion() throws IOException {
        return ProcessUtil.execAndReturnString(directory, GIT, "--version");
    }
    
    public boolean isGitAvailable() {
        try {
            checkGitVersion();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
    
    
    
    public File getGitDir() throws IOException {
        return new File(ProcessUtil.execAndReturnString(directory, GIT, "rev-parse", "--git-dir"));
        
        
    }
    
    public GitRemotes getRemotes() throws IOException {
        GitRemotes out = new GitRemotes();
        
        String res = ProcessUtil.execAndReturnString(directory, GIT, "remote", "-v");
        Scanner scanner = new Scanner(res);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            GitRemote remote = new GitRemote();
            String[] parts = line.split("\\s+");
            System.out.println(Arrays.toString(parts));
            remote.setName(parts[0]);
            remote.setUrl(parts[1]);
            remote.setType(parts[2]);
            out.add(remote);
        }
        return out;
        
    }
}
