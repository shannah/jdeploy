package ca.weblite.jdeploy.installer.services;

import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.json.JSONObject;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;


public class WebInstallerService {
    private class WebInstallerResponse {
        private final JSONObject json;

        private WebInstallerResponse(JSONObject json) {
            this.json = json;

        }

        public String getStatusURL() {
            if (json.has("statusUrl")) {
                return json.getString("statusUrl");
            }
            return null;
        }

        public String getWizardURL() {
            if (json.has("wizardUrl")) {
                return json.getString("wizardUrl");
            }
            return null;
        }

        public WebInstallationStatus getStatus() {
            if (json.has("status")) {
                switch (json.getString("status")) {
                    case "success": return WebInstallationStatus.SUCCESS;
                    case "failed": return WebInstallationStatus.FAILED;
                    case "cancelled":
                    case "canceled":
                        return WebInstallationStatus.CANCELLED;
                }
            }
            return WebInstallationStatus.PENDING;
        }

        public JSONObject getReceiptDetails() {
            if (json.has("receiptDetails")) {
                return json.getJSONObject("receiptDetails");
            }
            return null;
        }
    }

    public class WebInstallationResult {

        WebInstallationStatus status;
        private Throwable error;

        private WebInstallerResponse endpointResponse;

        public String getStatusURL() {
            if (endpointResponse == null) return null;
            return endpointResponse.getStatusURL();
        }

        public String getWizardURL() {
            if (endpointResponse == null) return null;
            return endpointResponse.getWizardURL();
        }

        public WebInstallationStatus getStatus() {
            if (status != null) return status;
            if (endpointResponse == null) return WebInstallationStatus.PENDING;
            return endpointResponse.getStatus();
        }

        public boolean isComplete() {
            return !(getStatus() == WebInstallationStatus.PENDING);
        }

        public JSONObject getReceiptDetails() {
            if (endpointResponse != null) return endpointResponse.getReceiptDetails();
            return null;
        }
    }

    public enum WebInstallationStatus {
        PENDING,
        SUCCESS,
        FAILED,
        CANCELLED;
    }

    public CompletableFuture<WebInstallationResult> startWebInstall(String endpointURL) {
        WebInstallationResult result = new WebInstallationResult();
        CompletableFuture<WebInstallationResult> future = new CompletableFuture<>();

        Runnable r = ()->{
            try {
                WebInstallerResponse endpointResponse = new WebInstallerResponse(fetchAsJSON(endpointURL));
                result.endpointResponse = endpointResponse;

            } catch (IOException ex) {
                result.error = ex;
                result.status = WebInstallationStatus.FAILED;
                return;
            }

            if (result.getStatusURL() == null) {
                result.error = new IllegalStateException("Endpoint URL response did not include a statusUrl entry");
                result.status = WebInstallationStatus.FAILED;
                return;
            }

            String statusURL = result.getStatusURL();
            String wizardURL = result.getWizardURL();

            if (wizardURL != null) {
                // The response supplied a wizard URL, so we direct the user to that URL.
                if (!Desktop.isDesktopSupported()) {
                    result.error = new UnsupportedOperationException("This platform does not support desktop APIs, so it cannot open the wizard URL.");
                    result.status = WebInstallationStatus.FAILED;
                    return;
                }

                try {
                    Desktop.getDesktop().browse(new URI(wizardURL));
                } catch (Exception e) {
                    result.error = e;
                    result.status = WebInstallationStatus.FAILED;
                    return;
                }
            }
            while (!result.isComplete()) {
                try {
                    WebInstallerResponse endpointResponse = new WebInstallerResponse(fetchAsJSON(statusURL));
                    result.endpointResponse = endpointResponse;

                } catch (IOException ex) {
                    result.error = ex;
                    result.status = WebInstallationStatus.FAILED;
                    return;
                }

                try {
                    Thread.sleep(2000L);
                } catch (Exception ex) {
                    result.error = ex;
                    result.status = WebInstallationStatus.FAILED;
                }
            }

        };

        new Thread(() -> {
            r.run();
            future.complete(result);
        }).start();

        return future;

    }

    private JSONObject fetchAsJSON(String url) throws IOException {
        try (InputStream inputStream = URLUtil.openStream(new URL(url))) {
            return new JSONObject(IOUtil.readToString(inputStream));
        }
    }
}
