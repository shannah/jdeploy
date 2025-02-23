package ca.weblite.jdeploy.http;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class StaticFileServer extends NanoHTTPD {
    private final File rootDir;

    public StaticFileServer(int port, File rootDir) {
        super(port);
        this.rootDir = rootDir;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.equals("/")) uri = "/index.html"; // Default file

        File file = new File(rootDir, uri);
        if (file.isDirectory()) {
            file = new File(file, "index.html"); // Serve index.html in directories
        }

        if (file.exists() && file.isFile()) {
            return serveFile(file);
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    private Response serveFile(File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream"; // Fallback for unknown types

            FileInputStream fis = new FileInputStream(file);
            return newChunkedResponse(Response.Status.OK, mimeType, fis);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
    }

    public static void main(String[] args) {
        File wwwroot = new File(args.length > 0 ? args[0] : ".");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

        try {
            StaticFileServer server = new StaticFileServer(port, wwwroot);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            System.out.println("Serving " + wwwroot + " at http://localhost:" + port);
        } catch (IOException e) {
            System.err.println("Couldn't start server:\n" + e);
        }
    }
}
