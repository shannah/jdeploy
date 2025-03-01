package ca.weblite.jdeploy.gui.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class SwingOneTimePasswordProvider implements OneTimePasswordProviderInterface {

    private final Component parentComponent;

    public SwingOneTimePasswordProvider(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    @Override
    public String promptForOneTimePassword(PublishingContext context, PublishTargetInterface target) {
        if (EventQueue.isDispatchThread()) {
            return JOptionPane.showInputDialog(
                    parentComponent,
                    "Please enter One Time Password for " + target.getName() + ": ",
                    "One Time Password",
                    JOptionPane.QUESTION_MESSAGE
            );
        } else {
            final String[] result = new String[1];
            try {
                EventQueue.invokeAndWait(() -> {
                    result[0] = JOptionPane.showInputDialog(
                            parentComponent,
                            "Please enter One Time Password for " + target.getName() + ": ",
                            "One Time Password",
                            JOptionPane.QUESTION_MESSAGE
                    );
                });
            } catch (InterruptedException e) {
                return "";
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return result[0];
        }
    }
}
