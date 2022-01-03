/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.event;

import java.net.URL;

/**
 *
 * @author shannah
 */
public interface DownloadProgressListener {
    public void progressChanged(URL url, long totalBytes, long totalReceived, double estimatedProgress);
}
