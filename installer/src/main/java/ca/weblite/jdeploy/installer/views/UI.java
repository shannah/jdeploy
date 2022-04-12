package ca.weblite.jdeploy.installer.views;

public interface UI {
    public void run(Runnable r);
    public boolean isOnUIThread();
}
