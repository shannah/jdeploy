package ca.weblite.jdeploy.http;

import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;

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
            return serveFile(session, file);
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    private Response serveFile(IHTTPSession session, File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream"; // Fallback for unknown types

            long fileLength = file.length();
            Map<String, String> headers = session.getHeaders();
            String rangeHeader = headers.get("range");

            if (rangeHeader != null) {
                return servePartialContent(file, mimeType, rangeHeader, fileLength);
            }

            // Serve full file if no Range header is present
            FileInputStream fis = new FileInputStream(file);
            Response response = newChunkedResponse(Response.Status.OK, mimeType, fis);
            response.addHeader("Content-Length", String.valueOf(fileLength));
            response.addHeader("Accept-Ranges", "bytes");
            return response;

        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "500 Internal Server Error");
        }
    }

    private Response servePartialContent(File file, String mimeType, String rangeHeader, long fileLength) {
        try {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            long start = Long.parseLong(ranges[0]);
            long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : fileLength - 1;

            if (start >= fileLength || end >= fileLength || start > end) {
                return newFixedLengthResponse(Response.Status.lookup(416), "text/plain", "416 Range Not Satisfiable");
            }

            long contentLength = end - start + 1;
            FileInputStream fis = new FileInputStream(file);
            fis.skip(start);

            Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, contentLength);
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.addHeader("Content-Length", String.valueOf(contentLength));
            response.addHeader("Accept-Ranges", "bytes");

            return response;

        } catch (IOException | NumberFormatException e) {
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
