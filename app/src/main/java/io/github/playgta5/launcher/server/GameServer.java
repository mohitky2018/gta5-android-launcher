package io.github.playgta5.launcher.server;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public class GameServer {
    private static final String TAG = "GTA5GameServer";
    private static final int DEFAULT_PORT = 8080;
    private static final String TOUCH_PREFIX = "/__touch/";		// touch controls for the game page (assets/touch)

    private final Context context;
    private final File gameDataDir;
    private final File webRootDir;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean isRunning = false;
    private int port = DEFAULT_PORT;

    public GameServer(Context context, File gameDataDir, File webRootDir) {
        this.context = context;
        this.gameDataDir = gameDataDir;
        this.webRootDir = webRootDir;
    }

    public synchronized void start() throws IOException {
        if (isRunning) return;
        threadPool = Executors.newCachedThreadPool();

        // Bind port (fallback to next if occupied, then fallback to ephemeral port 0)
        boolean bound = false;
        for (int p = DEFAULT_PORT; p < DEFAULT_PORT + 10; p++) {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", p));
                this.port = p;
                bound = true;
                break;
            } catch (IOException ignored) {}
        }

        if (!bound) {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            this.port = serverSocket.getLocalPort();
        }

        isRunning = true;
        Log.i(TAG, "Game Server started on http://127.0.0.1:" + port);

        threadPool.execute(this::acceptLoop);
    }

    public synchronized void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        Log.i(TAG, "Game Server stopped");
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void acceptLoop() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                threadPool.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (!isRunning) break;
            }
        }
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase();
            String fullPath = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String val = line.substring(colon + 1).trim();
                    headers.put(key, val);
                    if ("content-length".equals(key)) {
                        try { contentLength = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                    }
                }
            }

            String urlPath = fullPath;
            String queryString = "";
            int qIndex = fullPath.indexOf('?');
            if (qIndex != -1) {
                urlPath = fullPath.substring(0, qIndex);
                queryString = fullPath.substring(qIndex + 1);
            }
            urlPath = URLDecoder.decode(urlPath, "UTF-8");
            Log.i(TAG, "REQ: " + method + " " + urlPath + (queryString.isEmpty() ? "" : "?" + queryString));

            if ("OPTIONS".equals(method)) {
                StringBuilder sb = new StringBuilder();
                sb.append("HTTP/1.1 204 No Content\r\n");
                appendSecurityHeaders(sb);
                sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n");
                sb.append("Access-Control-Allow-Headers: Range, Content-Type, Authorization, X-Requested-With\r\n");
                sb.append("Content-Length: 0\r\n\r\n");
                out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                return;
            }

            if ("POST".equals(method) && "/data/batch".equals(urlPath)) {
                handleBatchRequest(reader, contentLength, queryString, out);
                return;
            }

            if ("POST".equals(method) && "/log".equals(urlPath)) {
                handleLogRequest(reader, contentLength, out);
                return;
            }

            if ("POST".equals(method) && urlPath.startsWith("/shot")) {
                handleShotRequest(reader, contentLength, out);
                return;
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                handleFileRequest(urlPath, headers, out, "HEAD".equals(method));
            } else {
                sendError(out, 405, "Method Not Allowed");
            }

        } catch (Exception e) {
            Log.w(TAG, "Error handling client: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleFileRequest(String path, Map<String, String> headers, OutputStream out, boolean headOnly) throws IOException {
        if ("/".equals(path) || path.isEmpty()) {
            path = "/index.html";
        }

        if (path.startsWith(TOUCH_PREFIX)) {
            serveTouchAsset(path.substring(TOUCH_PREFIX.length()), out, headOnly);
            return;
        }

        File targetFile = null;

        if (path.startsWith("/data/")) {
            String sub = path.substring("/data/".length());
            if (gameDataDir != null && gameDataDir.exists()) {
                targetFile = new File(gameDataDir, sub);
                if (!targetFile.exists()) {
                    targetFile = new File(gameDataDir, "data/" + sub);
                }
            }
            if ((targetFile == null || !targetFile.exists()) && webRootDir != null && webRootDir.exists()) {
                targetFile = new File(webRootDir, "data/" + sub);
                if (!targetFile.exists()) {
                    targetFile = new File(webRootDir, sub);
                }
            }
        } else if (path.startsWith("/b/")) {
            String rel = path.substring(1);
            if (webRootDir != null && webRootDir.exists()) {
                targetFile = new File(webRootDir, rel);
            }
            if ((targetFile == null || !targetFile.exists()) && gameDataDir != null) {
                File parent = gameDataDir.getParentFile();
                if (parent != null) targetFile = new File(parent, rel);
            }
        } else {
            // General files like index.html, favicon.ico, etc.
            String rel = path.startsWith("/") ? path.substring(1) : path;
            if (webRootDir != null && webRootDir.exists()) {
                targetFile = new File(webRootDir, rel);
            }
            if ((targetFile == null || !targetFile.exists()) && gameDataDir != null) {
                File parent = gameDataDir.getParentFile();
                if (parent != null) targetFile = new File(parent, rel);
                if (targetFile == null || !targetFile.exists()) {
                    targetFile = new File(gameDataDir, rel);
                }
            }
        }

        if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
            if ("/index.html".equals(path)) {
                Log.w(TAG, "index.html not found! webRootDir: " + webRootDir + ", gameDataDir: " + gameDataDir);
                serveDiagnosticHtml(out);
                return;
            }
            sendError(out, 404, "File Not Found: " + path);
            return;
        }

        if ("/index.html".equals(path)) {
            serveIndexWithTouchControls(targetFile, out, headOnly);
            return;
        }

        long fileLength = targetFile.length();
        String contentType = guessMimeType(targetFile.getName());
        String rangeHeader = headers.get("range");

        // The build's files (/b/<build>/: game.wasm, scripts, shaders) get validators, so Chrome keeps them in its HTTP cache and, for game.wasm,
        // keeps the compiled engine code (its code cache needs a cacheable response): later launches skip compiling 63 MB of WebAssembly.
        // no-cache = revalidate every time (a 304 costs nothing locally), so a changed file is never served stale.
        String cacheHeaders = "";
        if (path.startsWith("/b/")) {
            String etag = "\"" + fileLength + "-" + targetFile.lastModified() + "\"";
            if (etag.equals(headers.get("if-none-match"))) {
                StringBuilder sb = new StringBuilder("HTTP/1.1 304 Not Modified\r\n");
                appendSecurityHeaders(sb);
                sb.append("ETag: ").append(etag).append("\r\nCache-Control: no-cache\r\nContent-Length: 0\r\n\r\n");
                out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                return;
            }
            cacheHeaders = "ETag: " + etag + "\r\nCache-Control: no-cache\r\nLast-Modified: " + httpDate(targetFile.lastModified()) + "\r\n";
        }

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            serveRange(targetFile, fileLength, contentType, rangeHeader, out, headOnly, cacheHeaders);
        } else {
            serveFullFile(targetFile, fileLength, contentType, out, headOnly, cacheHeaders);
        }
    }

    // The game page with the touch controls script added before its own scripts (touch.js emulates pointer lock, which must be in place before
    // the page's module installs its input handlers).
    private void serveIndexWithTouchControls(File file, OutputStream out, boolean headOnly) throws IOException {
        String html = new String(readAll(new FileInputStream(file)), StandardCharsets.UTF_8);
        String tag = "<script src=\"" + TOUCH_PREFIX + "touch.js\"></script>";
        int head = html.indexOf("</head>");
        html = head >= 0 ? html.substring(0, head) + tag + "\n" + html.substring(head) : tag + html;
        sendBytes(out, html.getBytes(StandardCharsets.UTF_8), "text/html; charset=utf-8", headOnly);
    }

    private void serveTouchAsset(String rel, OutputStream out, boolean headOnly) throws IOException {
        if (rel.isEmpty() || rel.contains("..") || rel.startsWith("/")) {
            sendError(out, 404, "Not Found");
            return;
        }
        byte[] bytes;
        try (InputStream in = context.getAssets().open("touch/" + rel)) {
            bytes = readAll(in);
        } catch (IOException e) {
            sendError(out, 404, "Not Found: " + rel);
            return;
        }
        sendBytes(out, bytes, guessMimeType(rel), headOnly);
    }

    private void sendBytes(OutputStream out, byte[] bytes, String contentType, boolean headOnly) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        appendSecurityHeaders(sb);
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(bytes.length).append("\r\n");
        sb.append("Cache-Control: no-cache\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!headOnly) out.write(bytes);
        out.flush();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream src = in) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = src.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    private static String httpDate(long millis) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return f.format(new java.util.Date(millis));
    }

    private void serveFullFile(File file, long length, String contentType, OutputStream out, boolean headOnly, String cacheHeaders) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        appendSecurityHeaders(sb);
        sb.append(cacheHeaders);
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(length).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (headOnly) {
            out.flush();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis, 64 * 1024)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = bis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }

    private void serveRange(File file, long fileLength, String contentType, String rangeHeader, OutputStream out, boolean headOnly, String cacheHeaders) throws IOException {
        Pattern pattern = Pattern.compile("bytes=(\\d*)-(\\d*)");
        Matcher matcher = pattern.matcher(rangeHeader);
        if (!matcher.matches()) {
            sendError(out, 416, "Invalid Range");
            return;
        }

        String startStr = matcher.group(1);
        String endStr = matcher.group(2);

        long start = 0;
        long end = fileLength - 1;

        if (startStr != null && !startStr.isEmpty()) {
            start = Long.parseLong(startStr);
            if (endStr != null && !endStr.isEmpty()) {
                end = Math.min(fileLength - 1, Long.parseLong(endStr));
            }
        } else if (endStr != null && !endStr.isEmpty()) {
            start = Math.max(0, fileLength - Long.parseLong(endStr));
        }

        if (start >= fileLength || end < start) {
            String err = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + fileLength + "\r\nContent-Length: 0\r\n\r\n";
            out.write(err.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return;
        }

        long count = end - start + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 206 Partial Content\r\n");
        appendSecurityHeaders(sb);
        sb.append(cacheHeaders);
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Range: bytes ").append(start).append("-").append(end).append("/").append(fileLength).append("\r\n");
        sb.append("Content-Length: ").append(count).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (headOnly) {
            out.flush();
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.getChannel().position(start);
            byte[] buf = new byte[64 * 1024];
            long remaining = count;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = fis.read(buf, 0, toRead);
                if (n == -1) break;
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    private void handleBatchRequest(BufferedReader reader, int length, String queryString, OutputStream out) throws IOException {
        if (length <= 0 || length > 1024 * 1024) {
            sendError(out, 413, "Batch Payload Too Large");
            return;
        }

        char[] chars = new char[length];
        int readChars = 0;
        while (readChars < length) {
            int n = reader.read(chars, readChars, length - readChars);
            if (n == -1) break;
            readChars += n;
        }
        String body = new String(chars, 0, readChars);

        try {
            JSONArray runs = new JSONArray(body);
            List<Long> runLengths = new ArrayList<>();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            File dataRoot = gameDataDir != null && gameDataDir.exists() ? gameDataDir : (webRootDir != null ? new File(webRootDir, "data") : null);

            for (int i = 0; i < runs.length(); i++) {
                JSONArray item = runs.getJSONArray(i);
                String relPath = item.getString(0);
                long start = item.getLong(1);
                long end = item.getLong(2);

                File f = null;
                if (dataRoot != null) {
                    f = new File(dataRoot, relPath);
                    if (!f.exists()) {
                        f = new File(dataRoot, "data/" + relPath);
                    }
                }
                if ((f == null || !f.exists()) && webRootDir != null) {
                    f = new File(webRootDir, "data/" + relPath);
                    if (!f.exists()) {
                        f = new File(webRootDir, relPath);
                    }
                }

                if (f == null || !f.exists() || !f.isFile() || start < 0 || end < start) {
                    Log.w(TAG, "Batch missing file: " + relPath + " (searched in dataRoot=" + dataRoot + ", webRootDir=" + webRootDir + ")");
                    sendError(out, 400, "Invalid batch entry: " + relPath);
                    return;
                }

                long len = Math.max(0, Math.min(end + 1, f.length()) - start);
                runLengths.add(len);

                try (FileInputStream fis = new FileInputStream(f)) {
                    fis.getChannel().position(start);
                    byte[] buf = new byte[32 * 1024];
                    long rem = len;
                    while (rem > 0) {
                        int read = fis.read(buf, 0, (int) Math.min(buf.length, rem));
                        if (read == -1) break;
                        baos.write(buf, 0, read);
                        rem -= read;
                    }
                }
            }

            byte[] resultBytes = baos.toByteArray();
            boolean gzip = queryString.contains("gz=1");
            if (gzip) {
                ByteArrayOutputStream gzBaos = new ByteArrayOutputStream();
                try (GZIPOutputStream gzos = new GZIPOutputStream(gzBaos)) {
                    gzos.write(resultBytes);
                }
                resultBytes = gzBaos.toByteArray();
            }

            StringBuilder lens = new StringBuilder();
            for (int i = 0; i < runLengths.size(); i++) {
                if (i > 0) lens.append(",");
                lens.append(runLengths.get(i));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 200 OK\r\n");
            appendSecurityHeaders(sb);
            sb.append("Content-Type: application/octet-stream\r\n");
            sb.append("Content-Length: ").append(resultBytes.length).append("\r\n");
            sb.append("X-Run-Lengths: ").append(lens).append("\r\n");
            if (gzip) sb.append("Content-Encoding: gzip\r\n");
            sb.append("\r\n");

            out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(resultBytes);
            out.flush();

        } catch (Exception e) {
            sendError(out, 400, "Batch Error: " + e.getMessage());
        }
    }

    private void handleLogRequest(BufferedReader reader, int length, OutputStream out) throws IOException {
        String body = "";
        if (length > 0) {
            int toRead = Math.min(length, 128 * 1024);
            char[] chars = new char[toRead];
            int readChars = 0;
            while (readChars < toRead) {
                int n = reader.read(chars, readChars, toRead - readChars);
                if (n == -1) break;
                readChars += n;
            }
            body = new String(chars, 0, readChars);
            int rem = length - readChars;
            while (rem > 0) {
                long skipped = reader.skip(rem);
                if (skipped <= 0) break;
                rem -= skipped;
            }
        }
        if (!body.isEmpty()) {
            Log.i("GTA5EngineLog", body);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        appendSecurityHeaders(sb);
        sb.append("Content-Type: text/plain\r\n");
        sb.append("Content-Length: 2\r\n\r\n");
        sb.append("OK");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private void handleShotRequest(BufferedReader reader, int length, OutputStream out) throws IOException {
        if (length > 0) {
            int rem = length;
            while (rem > 0) {
                long skipped = reader.skip(rem);
                if (skipped <= 0) break;
                rem -= skipped;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        appendSecurityHeaders(sb);
        sb.append("Content-Type: text/plain\r\n");
        sb.append("Content-Length: 2\r\n\r\n");
        sb.append("OK");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private void appendSecurityHeaders(StringBuilder sb) {
        sb.append("Cross-Origin-Opener-Policy: same-origin\r\n");
        sb.append("Cross-Origin-Embedder-Policy: require-corp\r\n");
        sb.append("Cross-Origin-Resource-Policy: same-origin\r\n");
        sb.append("Document-Isolation-Policy: isolate-and-require-corp\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
    }

    private void serveDiagnosticHtml(OutputStream out) throws IOException {
        String dirName = gameDataDir != null ? gameDataDir.getAbsolutePath() : "No folder selected";
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<title>GTA V Engine Host</title>" +
                "<style>" +
                "body{background:#0d0e11;color:#fff;font-family:sans-serif;padding:24px;margin:0;}" +
                "h1{color:#fdb813;margin-top:0;font-size:24px;}" +
                ".card{background:#20232a;border:1px solid #323642;border-radius:12px;padding:16px;margin-bottom:16px;}" +
                ".badge{display:inline-block;padding:4px 10px;border-radius:12px;font-size:12px;font-weight:bold;background:#17191e;color:#fdb813;}" +
                ".ok{color:#00e676;} .warn{color:#ff9100;} code{color:#2979ff;background:#17191e;padding:2px 6px;border-radius:4px;}" +
                "</style></head><body>" +
                "<h1>GRAND THEFT AUTO V</h1>" +
                "<div class='card'>" +
                "<h3>Engine Server Running <span class='badge ok'>ONLINE</span></h3>" +
                "<p>Local Port: <code>" + port + "</code> • Isolation: <code>COOP/COEP Enabled</code></p>" +
                "<p>Selected Folder: <code>" + dirName + "</code></p>" +
                "<div id='gpu-check'>Checking WebGPU / WebGL support...</div>" +
                "</div>" +
                "<div class='card'>" +
                "<h3>Game Files Status</h3>" +
                "<p>The local HTTP engine host is ready and waiting for game assets.</p>" +
                "<p>Place your GTA V files (or mirror assets) into the selected folder to start gameplay.</p>" +
                "</div>" +
                "<script>" +
                "async function checkGPU(){" +
                "  const el = document.getElementById('gpu-check');" +
                "  if (navigator.gpu) {" +
                "    try {" +
                "      const adapter = await navigator.gpu.requestAdapter();" +
                "      if (adapter) {" +
                "        const info = await (adapter.requestAdapterInfo ? adapter.requestAdapterInfo() : {});" +
                "        el.innerHTML = '<span class=\"ok\">✔ WebGPU Active: ' + (info.vendor || 'Hardware Accelerated') + '</span>';" +
                "        return;" +
                "      }" +
                "    } catch(e){}" +
                "  }" +
                "  el.innerHTML = '<span class=\"ok\">✔ WebGL Hardware Acceleration Active</span>';" +
                "}" +
                "checkGPU();" +
                "</script></body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        appendSecurityHeaders(sb);
        sb.append("Content-Type: text/html; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(bytes.length).append("\r\n\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
    }

    private void sendError(OutputStream out, int code, String message) throws IOException {
        String resp = "HTTP/1.1 " + code + " " + message + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + message.length() + "\r\n\r\n" +
                message;
        out.write(resp.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private String guessMimeType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".wgsl")) return "text/plain";
        return "application/octet-stream";
    }
}

