/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.event;

/**
 *
 * @author shannah
 */
public interface TaskProgressListener {
    public void progressChanged(String taskName, int totalTasks, int totalTasksCompleted, double estimatedProgress);
}
