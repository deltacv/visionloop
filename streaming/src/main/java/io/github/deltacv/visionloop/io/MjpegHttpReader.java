package io.github.deltacv.visionloop.io;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of a connection to motion jpeg (multipart/x-mixed-replace) stream.
 *
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a><br>
 *
 * Adapted from <a href="https://github.com/arskov/multipart-x-mixed-replace-java-player/">a GitHub repo</a> by Arseny Kovalchuk<br>
 * All credits due to the original author. Adaptation made by deltacv under the original MIT license.
 */
@SuppressWarnings("unused")
public class MjpegHttpReader implements Iterable<byte[]> {

    private final static String MULTIPART_MIXED_REPLACE = "multipart/x-mixed-replace";
    private final static String BOUNDARY_PART = "boundary=";
    private final static String CONTENT_LENGTH_HEADER = "content-length";

    private String boundaryPart;
    private final URL url;
    private HttpURLConnection connection;
    private boolean connected = false;

    public MjpegHttpReader(URL url) {
        this.url = url;
    }

    public MjpegHttpReader(String url) throws IOException {
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

    private static class ImagesIterator implements Iterator<byte[]> {

        private final byte[] boundary;
        private final byte[] boundaryEnd;
        private final InputStream stream;
        private boolean hasNext;

        private byte[] frame;

        private static final byte[] CRLF = {'\r', '\n'};
        private static final byte[] COLON_SPACE = {':', ' '};
        private static final Logger logger = LoggerFactory.getLogger(ImagesIterator.class);

        ImagesIterator(String boundaryPart, HttpURLConnection conn) throws IOException {
            this.boundary = ("--" + boundaryPart).getBytes(StandardCharsets.US_ASCII);
            this.boundaryEnd = (new String(this.boundary) + "--").getBytes(StandardCharsets.US_ASCII);

            logger.info("Boundary: {}", new String(boundary, StandardCharsets.US_ASCII));

            this.stream = new BufferedInputStream(conn.getInputStream(), 8192);
            this.hasNext = true;
        }

        private final byte[] lineBuffer = new byte[8192];
        private int lineLength;

        private int readLine() throws IOException {
            lineLength = 0;

            long startTime = System.currentTimeMillis();
            long timeout = 3000; // 3 seconds

            int nextByte;
            while ((nextByte = stream.read()) != -1) {
                if (nextByte == '\n') break;
                if (lineLength < lineBuffer.length) {
                    if (nextByte != '\r') lineBuffer[lineLength++] = (byte) nextByte;
                } else {
                    throw new IOException("Line buffer overflow");
                }

                if(System.currentTimeMillis() - startTime > timeout) {
                    throw new IOException("Timeout while reading line");
                }
            }

            return lineLength;
        }

        private void readUntilBoundary() throws IOException {
            while (hasNext) {
                int len = readLine();
                if (len == boundary.length && matches(lineBuffer, len, boundary)) break;
                if (len == boundaryEnd.length && matches(lineBuffer, len, boundaryEnd)) {
                    hasNext = false;
                    break;
                }
            }
        }

        private boolean matches(byte[] buffer, int len, byte[] toMatch) {
            if (len != toMatch.length) return false;
            for (int i = 0; i < len; i++) {
                if (buffer[i] != toMatch[i]) return false;
            }
            return true;
        }

        private Map<String, String> readHeaders() throws IOException {
            Map<String, String> headers = new HashMap<>();

            while (true) {
                int len = readLine();
                if (len == 0) break;

                int separatorIdx = indexOf(lineBuffer, len, COLON_SPACE);
                if (separatorIdx == -1) continue;

                String key = new String(lineBuffer, 0, separatorIdx, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                String value = new String(lineBuffer, separatorIdx + COLON_SPACE.length, len - separatorIdx - COLON_SPACE.length, StandardCharsets.US_ASCII);

                headers.put(key, value);
            }

            return headers;
        }

        private int indexOf(byte[] buffer, int len, byte[] toFind) {
            for (int i = 0; i <= len - toFind.length; i++) {
                boolean found = true;
                for (int j = 0; j < toFind.length; j++) {
                    if (buffer[i + j] != toFind[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) return i;
            }
            return -1;
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
                    String contentLengthHeader = headers.get("content-length");

                    int length;
                    try {
                        length = Integer.parseInt(contentLengthHeader);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid content length", e);
                    }

                    if (frame == null || frame.length < length) {
                        frame = new byte[length];
                    }

                    int bytesRead = 0;

                    long startTime = System.currentTimeMillis();
                    final long timeoutMs = 2000; // 2 seconds timeout

                    while (bytesRead < length) {
                        if (System.currentTimeMillis() - startTime > timeoutMs) {
                            throw new IOException("Timeout while reading MJPEG frame");
                        }

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
    }
}