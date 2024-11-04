package io.github.deltacv.visionloop.io;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Implementation of a connection to motion jpeg (multipart/x-mixed-replace) stream, and using it as an Itarable like this:
 *   <pre>
 *   public static void main(String... strings) {
 *       VideoSource src = new VideoSource("http://91.85.203.9/axis-cgi/mjpg/video.cgi");
 *       try {
 *           src.connect();
 *           for (byte[] img : src) {
 *               Files.write(Paths.get("c:/tmp/mjpeg/" + UUID.randomUUID().toString() + ".jpg"), img);
 *           }
 *       } catch (IOException e) {
 *           e.printStackTrace();
 *       }
 *   }
 *   </pre>
 *
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a><br>
 *
 * Adapted from <a href="https://github.com/arskov/multipart-x-mixed-replace-java-player/">a GitHub repo</a> by Arseny Kovalchuk<br>
 * All credits due to the original author. Adaptation made by deltacv under the original MIT license.
 */
public class MJpegHttpReader implements Iterable<byte[]> {

    private final static String MULTIPART_MIXED_REPLACE = "multipart/x-mixed-replace";
    private final static String BOUNDARY_PART = "boundary=";
    private final static String CONTENT_LENGTH_HEADER = "content-length";

    private String boundaryPart;
    private final URL url;
    private HttpURLConnection connection;
    private boolean connected = false;

    public MJpegHttpReader(URL url) {
        this.url = url;
    }

    public MJpegHttpReader(String url) throws IOException {
        this.url = new URL(url);
    }

    public void start() {
        if (connected) throw new IllegalStateException("Already connected");

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            String contentType = connection.getContentType();
            if (contentType != null && !contentType.startsWith(MULTIPART_MIXED_REPLACE)) {
                throw new IOException("Unsupported Content-Type: " + contentType);
            }

            assert contentType != null;
            boundaryPart = contentType.substring(contentType.indexOf(BOUNDARY_PART) + BOUNDARY_PART.length());
            connected = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to start MJPEG reader", e);
        }
    }

    public void stop() {
        if (connection != null) {
            connection.disconnect();
        }
        connected = false;
    }

    @NotNull
    @Override
    public Iterator<byte[]> iterator() {
        try {
            return new ImagesIterator(boundaryPart, connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class ImagesIterator implements Iterator<byte[]> {

        private final String boundary;
        private final InputStream stream;
        private boolean hasNext;

        private byte[] frame;

        private Logger logger = LoggerFactory.getLogger(ImagesIterator.class);

        ImagesIterator(String boundaryPart, HttpURLConnection conn) throws IOException {
            this.boundary = boundaryPart.startsWith("--") ? boundaryPart : "--" + boundaryPart;
            logger.info("Boundary: {}", boundary);

            this.stream = new BufferedInputStream(conn.getInputStream(), 8192);
            this.hasNext = true;
        }

        private final StringBuilder lineBuffer = new StringBuilder();

        private String readLine() throws IOException {
            lineBuffer.setLength(0);  // Clear the buffer for reuse
            int nextByte;

            while ((nextByte = stream.read()) != -1) {
                if (nextByte == '\n') break;
                if (nextByte != '\r') lineBuffer.append((char) nextByte);
            }

            return lineBuffer.toString().trim();
        }

        private void readUntilBoundary() throws IOException {
            while (hasNext) {
                String line = readLine();
                if (line.equals(boundary)) break;
                if (line.equals(boundary + "--")) {
                    hasNext = false;
                    break;
                }
            }
        }

        StringBuilder keyBuilder = new StringBuilder();
        StringBuilder valueBuilder = new StringBuilder();

        private Map<String, String> readHeaders() throws IOException {
            Map<String, String> headers = new HashMap<>();

            while (true) {
                String line = readLine();
                if (line.isEmpty()) break;

                keyBuilder.setLength(0);   // Clear the key builder
                valueBuilder.setLength(0); // Clear the value builder
                boolean isKey = true;

                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (isKey && c == ':' && i + 1 < line.length() && line.charAt(i + 1) == ' ') {
                        // Found the separator ": "
                        isKey = false;
                        i++; // Skip the space after ":"
                    } else if (isKey) {
                        keyBuilder.append(Character.toLowerCase(c)); // Build the key in lowercase
                    } else {
                        valueBuilder.append(c); // Build the value
                    }
                }

                headers.put(keyBuilder.toString(), valueBuilder.toString());
            }

            return headers;
        }

        @Override
        public boolean hasNext() {
            synchronized (this) {
                return this.hasNext;
            }
        }

        @Override
        public byte[] next() {
            synchronized (this) {
                try {
                    readUntilBoundary();
                    Map<String, String> headers = readHeaders();
                    String contentLengthHeader = headers.get(CONTENT_LENGTH_HEADER);

                    int length;
                    try {
                        length = Integer.parseInt(contentLengthHeader);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid content length", e);
                    }

                    if(frame == null || frame.length < length) {
                        frame = new byte[length];
                    }

                    int bytesRead = 0;

                    while (bytesRead < length) {
                        int read = stream.read(frame, bytesRead, length - bytesRead);
                        if (read == -1) throw new IOException("Unexpected end of stream");
                        bytesRead += read;
                    }

                    return frame;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read MJPEG frame", e);
                }
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}