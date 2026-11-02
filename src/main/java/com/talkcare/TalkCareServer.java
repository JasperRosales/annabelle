package com.talkcare;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.talkcare.models.ChatMessage;
import com.talkcare.models.Conversation;
import com.talkcare.models.Report;
import com.talkcare.models.User;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TalkCareServer {
    private final int port;
    private HttpServer server;
    private final DatabaseManager db;
    private final File uploadsDir;
    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    private record AuthSession(int userId, String name, String role) {}

    public TalkCareServer(int port) {
        this.port = port;
        this.db = DatabaseManager.getInstance();
        this.uploadsDir = new File("uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new ApiHandler());
        server.createContext("/uploads/", new UploadsHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("TalkCare Server is running at http://localhost:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public static void main(String[] args) throws IOException {
        TalkCareServer server = new TalkCareServer(54321);
        server.start();
        System.out.println("TalkCare backend server started on port 54321");
    }

    private class UploadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath().replaceFirst("^/uploads/", "");
            File target = new File(uploadsDir, path);
            if (target.exists() && target.isFile() && target.getCanonicalPath().startsWith(uploadsDir.getCanonicalPath())) {
                byte[] bytes = Files.readAllBytes(target.toPath());
                String mime = "application/octet-stream";
                if (path.endsWith(".png")) mime = "image/png";
                else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mime = "image/jpeg";
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                sendJsonResponse(exchange, 404, "{\"error\":\"File not found\"}");
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Look in classpath first
            String resourcePath = "static" + path;
            InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);

            // Also check disk if in dev environment
            byte[] content = null;
            if (in != null) {
                content = in.readAllBytes();
                in.close();
            } else {
                File local = new File("src/main/resources/static" + path);
                if (local.exists() && local.isFile()) {
                    content = Files.readAllBytes(local.toPath());
                }
            }

            if (content != null) {
                String mime = getMimeType(path);
                exchange.getResponseHeaders().set("Content-Type", mime);
                exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                // SPA fallback to index.html if not found
                InputStream indexIn = getClass().getClassLoader().getResourceAsStream("static/index.html");
                if (indexIn != null) {
                    byte[] indexBytes = indexIn.readAllBytes();
                    indexIn.close();
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, indexBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(indexBytes);
                    }
                } else {
                    File localIndex = new File("src/main/resources/static/index.html");
                    if (localIndex.exists()) {
                        byte[] indexBytes = Files.readAllBytes(localIndex.toPath());
                        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, indexBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(indexBytes);
                        }
                    } else {
                        String msg = "TalkCare App: index.html not found";
                        exchange.sendResponseHeaders(404, msg.length());
                        exchange.getResponseBody().write(msg.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
            }
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".json")) return "application/json; charset=UTF-8";
            return "text/plain";
        }
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            String body = readBody(exchange);

            try {
                if (path.equals("/api/auth/login")) {
                    if (!db.isConnectedToPostgres()) {
                        sendJsonResponse(exchange, 503,
                                "{\"error\":\"Account sign-in is temporarily unavailable because the database is disconnected. Reconnect PostgreSQL and try again.\"}");
                        return;
                    }
                    Map<String, String> data = parseJson(body);
                    String loginId = data.getOrDefault("username", data.get("email"));
                    String password = data.get("password");
                    try {
                        User user = db.login(loginId, password);
                        if (user != null) {
                            String token = createSession(user);
                            sendJsonResponse(exchange, 200, toJsonSession(user, token));
                        } else {
                            sendJsonResponse(exchange, 401, "{\"error\":\"Invalid username, email, full name, or password\"}");
                        }
                    } catch (SQLException e) {
                        System.err.println("DB login error: " + e.getMessage());
                        sendJsonResponse(exchange, 500, "{\"error\":\"Sign-in could not reach the account data. Check the PostgreSQL connection and retry.\"}");
                    }
                } else if (path.equals("/api/auth/register")) {
                    if (!db.isConnectedToPostgres()) {
                        sendJsonResponse(exchange, 503,
                                "{\"error\":\"Account creation is temporarily unavailable because the database is disconnected. Reconnect PostgreSQL and try again.\"}");
                        return;
                    }
                    Map<String, String> data = parseJson(body);
                    if (isBlank(data.get("username")) || isBlank(data.get("fullName"))
                            || isBlank(data.get("email")) || isBlank(data.get("password"))
                            || isBlank(data.get("contactNo")) || isBlank(data.get("address"))) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Please complete all required account fields.\"}");
                        return;
                    }
                    try {
                        User user = db.register(
                                data.get("username"),
                                data.get("fullName"),
                                data.get("email"),
                                data.get("password"),
                                data.get("contactNo"),
                                data.get("address")
                        );
                        if (user != null) {
                            String token = createSession(user);
                            sendJsonResponse(exchange, 201, toJsonSession(user, token));
                        } else {
                            sendJsonResponse(exchange, 500, "{\"error\":\"The database did not create the account. Please try again.\"}");
                        }
                    } catch (SQLException e) {
                        System.err.println("DB register error: " + e.getMessage());
                        if ("23505".equals(e.getSQLState())) {
                            sendJsonResponse(exchange, 409, "{\"error\":\"That username or email is already in use. Try a different one.\"}");
                        } else {
                            sendJsonResponse(exchange, 500, "{\"error\":\"The account could not be saved to PostgreSQL. Please try again.\"}");
                        }
                    }
                } else if (path.equals("/api/auth/logout")) {
                    String token = exchange.getRequestHeaders().getFirst("X-Session-Token");
                    if (token != null) sessions.remove(token);
                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if (path.equals("/api/auth/forgot-password")) {
                    if (!isPasswordResetEmailConfigured()) {
                        sendJsonResponse(exchange, 503, "{\"error\":\"Password recovery email is not configured. Contact the administrator.\"}");
                        return;
                    }
                    Map<String, String> data = parseJson(body);
                    DatabaseManager.PasswordResetRequest resetRequest =
                            db.createPasswordResetRequest(data.get("email"));
                    if (resetRequest != null) {
                        try {
                            sendPasswordResetEmail(resetRequest);
                        } catch (Exception e) {
                            db.invalidatePasswordResetToken(resetRequest.token());
                            System.err.println("Could not send password reset email: " + e.getMessage());
                            sendJsonResponse(exchange, 503, "{\"error\":\"Could not send the password reset email. Check the SMTP configuration and try again.\"}");
                            return;
                        }
                    }
                    sendJsonResponse(exchange, 200,
                            "{\"message\":\"If an account exists for that email, a password reset link has been sent.\"}");
                } else if (path.equals("/api/auth/reset-password")) {
                    Map<String, String> data = parseJson(body);
                    String newPassword = data.get("newPassword");
                    if (newPassword == null || newPassword.length() < 8) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Password must be at least 8 characters long.\"}");
                        return;
                    }
                    if (db.resetPassword(data.get("token"), newPassword)) {
                        sendJsonResponse(exchange, 200, "{\"message\":\"Password updated. You can now log in.\"}");
                    } else {
                        sendJsonResponse(exchange, 400, "{\"error\":\"This reset link is invalid, expired, or already used. Request a new link.\"}");
                    }
                } else if (path.equals("/api/auth/user")) {
                    int id = Integer.parseInt(queryParams.getOrDefault("id", "2"));
                    User u = db.getUserById(id);
                    if (u != null) {
                        sendJsonResponse(exchange, 200, toJson(u));
                    } else {
                        sendJsonResponse(exchange, 404, "{\"error\":\"User not found\"}");
                    }
                } else if (path.equals("/api/profile/update")) {
                    Map<String, String> data = parseJson(body);
                    int userId = Integer.parseInt(data.getOrDefault("userId", "2"));
                    boolean ok = db.updateProfile(
                            userId,
                            data.get("username"),
                            data.get("email"),
                            data.get("password"),
                            data.get("profilePictureUrl")
                    );
                    sendJsonResponse(exchange, 200, "{\"success\":" + ok + "}");
                } else if (path.equals("/api/profile/upload-photo")) {
                    Map<String, String> data = parseJson(body);
                    int userId = Integer.parseInt(data.getOrDefault("userId", "2"));
                    String base64Image = data.get("image"); // e.g. data:image/png;base64,....
                    String savedUrl = saveBase64File(base64Image, "profile_" + userId);
                    db.updateProfile(userId, null, null, null, savedUrl);
                    sendJsonResponse(exchange, 200, "{\"url\":\"" + savedUrl + "\"}");
                } else if (path.equals("/api/verification/submit-id")) {
                    Map<String, String> data = parseJson(body);
                    String userIdValue = data.get("userId");
                    String idType = data.get("idType");
                    String idNumber = data.get("idNumber");
                    String address = data.get("address");
                    String email = data.get("email");
                    String idDocBase64 = data.get("idDocImage");
                    if (userIdValue == null || idType == null || idType.isBlank()
                            || idNumber == null || idNumber.isBlank()
                            || address == null || address.isBlank()
                            || email == null || email.isBlank()
                            || idDocBase64 == null
                            || !idDocBase64.matches("(?is)^data:image/(png|jpe?g|gif|webp);base64,.+$")) {
                        sendJsonResponse(exchange, 400,
                                "{\"error\":\"ID type, ID number, address, email, and an ID image are required.\"}");
                        return;
                    }
                    int userId;
                    try {
                        userId = Integer.parseInt(userIdValue);
                    } catch (NumberFormatException e) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Invalid account. Please log in again.\"}");
                        return;
                    }
                    String savedDocUrl = saveBase64File(idDocBase64, "id_" + userId);
                    if (savedDocUrl == null || savedDocUrl.isBlank()) {
                        sendJsonResponse(exchange, 500, "{\"error\":\"The ID image could not be saved. Please try uploading it again.\"}");
                        return;
                    }
                    boolean ok = db.updateIdVerification(userId, idType, idNumber, address, email, savedDocUrl);
                    if (ok) {
                        sendJsonResponse(exchange, 200, "{\"success\":true,\"status\":\"PENDING_VERIFICATION\"}");
                    } else {
                        File savedDocument = new File(uploadsDir, new File(savedDocUrl).getName());
                        if (savedDocument.exists() && !savedDocument.delete()) {
                            System.err.println("Could not remove unlinked ID document: " + savedDocument.getName());
                        }
                        sendJsonResponse(exchange, 404, "{\"error\":\"Could not update this account. Please log in again and retry.\"}");
                    }
                } else if (path.equals("/api/reports/submit")) {
                    if (hasInvalidSessionToken(exchange)) {
                        sendJsonResponse(exchange, 401, "{\"error\":\"Your session has expired. Please log in again.\"}");
                        return;
                    }
                    AuthSession actor = findSession(exchange);
                    if (actor != null && !requireRole(exchange, actor, "USER")) return;
                    Map<String, String> data = parseJson(body);
                    if (isBlank(data.get("fullName")) || isBlank(data.get("email"))
                            || isBlank(data.get("address")) || isBlank(data.get("contactNo"))
                            || isBlank(data.get("concernType")) || isBlank(data.get("concernDetails"))) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Please complete all required report fields.\"}");
                        return;
                    }
                    Report r = new Report();
                    r.setFullName(data.get("fullName"));
                    r.setEmail(data.get("email"));
                    r.setAddress(data.get("address"));
                    r.setContactNo(data.get("contactNo"));
                    r.setConcernType(data.get("concernType"));
                    r.setInvolvedWitness(data.get("involvedWitness"));
                    r.setConcernDetails(data.get("concernDetails"));
                    Report saved = db.submitReport(r, actor == null ? null : actor.userId(),
                            actor == null ? null : actor.role());
                    sendJsonResponse(exchange, 201, toJson(saved));
                } else if (path.equals("/api/reports/list")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "USER", "ADMIN")) return;
                    String filter = queryParams.getOrDefault("filter", "All");
                    List<Report> list = db.getReports(filter, actor.userId(), actor.role());
                    sendJsonResponse(exchange, 200, toJsonReportList(list));
                } else if (path.equals("/api/reports/update-status")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    Map<String, String> data = parseJson(body);
                    int reportId = Integer.parseInt(data.get("reportId"));
                    String newStatus = data.get("status");
                    String action = "In Progress".equals(newStatus) ? "IN_PROGRESS"
                            : ("Resolved".equals(newStatus) ? "RESOLVED" : null);
                    if (action == null) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Choose In Progress or Resolved.\"}");
                        return;
                    }
                    db.processAdminRequest(reportId, "REPORT", action, null, null, actor.userId(), actor.role());
                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if (path.equals("/api/conversations")) {
                    AuthSession actor = findSession(exchange);
                    if (hasInvalidSessionToken(exchange)) {
                        sendJsonResponse(exchange, 401, "{\"error\":\"Your session has expired. Please sign in again.\"}");
                        return;
                    }
                    if (actor != null && !requireRole(exchange, actor, "USER")) return;
                    int clientId = actor == null ? 2 : actor.userId();
                    String serviceType = queryParams.getOrDefault("serviceType", "JUSTICE_CARE");
                    Conversation c = db.getOrCreateConversation(clientId, serviceType);
                    sendJsonResponse(exchange, 200, toJson(c));
                } else if (path.equals("/api/professional/conversations")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "THERAPIST", "CASE_HANDLER")) return;
                    sendJsonResponse(exchange, 200,
                            toJsonConversationList(db.getConversationsForProfessional(actor.userId())));
                } else if (path.equals("/api/messages")) {
                    int conversationId = Integer.parseInt(queryParams.getOrDefault("conversationId", "1"));
                    AuthSession actor = findSession(exchange);
                    if (hasInvalidSessionToken(exchange)) {
                        sendJsonResponse(exchange, 401, "{\"error\":\"Your session has expired. Please sign in again.\"}");
                        return;
                    }
                    requireConversationAccess(actor, db.getConversation(conversationId));
                    List<ChatMessage> list = db.getMessages(conversationId);
                    sendJsonResponse(exchange, 200, toJsonMessageList(list));
                } else if (path.equals("/api/messages/send")) {
                    AuthSession actor = findSession(exchange);
                    if (hasInvalidSessionToken(exchange)) {
                        sendJsonResponse(exchange, 401, "{\"error\":\"Your session has expired. Please sign in again.\"}");
                        return;
                    }
                    if (actor != null && !requireRole(exchange, actor, "USER")) return;
                    Map<String, String> data = parseJson(body);
                    int conversationId = Integer.parseInt(data.get("conversationId"));
                    Conversation conversation = db.getConversation(conversationId);
                    requireConversationAccess(actor, conversation);
                    int clientId = actor == null ? 2 : actor.userId();
                    String senderName = actor == null ? "Max Smith" : actor.name();
                    String messageText = Objects.toString(data.get("messageText"), "").trim();
                    String attachmentData = data.get("attachmentData");
                    if (isBlank(messageText) && isBlank(attachmentData)) {
                        throw new IllegalArgumentException("Write a message or attach a photo before sending.");
                    }
                    String attachmentUrl = saveChatAttachment(data.get("attachmentData"));

                    List<ChatMessage> added = db.sendClientMessage(conversationId, clientId, senderName, messageText, attachmentUrl);
                    sendJsonResponse(exchange, 200, toJsonMessageList(added));
                } else if (path.equals("/api/messages/reply")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "THERAPIST", "CASE_HANDLER")) return;
                    Map<String, String> data = parseJson(body);
                    int conversationId = Integer.parseInt(data.get("conversationId"));
                    requireConversationAccess(actor, db.getConversation(conversationId));
                    String messageText = Objects.toString(data.get("messageText"), "").trim();
                    if (isBlank(messageText)) {
                        throw new IllegalArgumentException("Write a reply before sending.");
                    }
                    ChatMessage reply = db.sendProfessionalReply(
                            conversationId, actor.userId(), actor.name(), messageText.trim());
                    sendJsonResponse(exchange, 200, toJson(reply));
                } else if (path.equals("/api/admin/metrics")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    Map<String, Object> stats = db.getAdminMetrics(actor.role());
                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"totalUsers\":").append(stats.get("totalUsers")).append(",");
                    sb.append("\"pendingApprovals\":").append(stats.get("pendingApprovals")).append(",");
                    sb.append("\"sessionToday\":").append(stats.get("sessionToday"));
                    sb.append("}");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if (path.equals("/api/admin/users")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    List<User> list = db.getActiveUserProfiles(actor.role());
                    sendJsonResponse(exchange, 200, toJsonUserList(list));
                } else if (path.equals("/api/admin/pending-requests")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    List<DatabaseManager.AdminRequest> requests = db.getAdminPendingRequests(actor.role());
                    sendJsonResponse(exchange, 200, toJsonAdminRequestList(requests));
                } else if (path.equals("/api/admin/pending-requests/action")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    Map<String, String> data = parseJson(body);
                    int requestId = Integer.parseInt(data.get("id"));
                    db.processAdminRequest(requestId, data.get("type"), data.get("action"),
                            data.get("reason"), data.get("remarks"), actor.userId(), actor.role());
                    sendJsonResponse(exchange, 200, "{\"success\":true}");
                } else if (path.equals("/api/admin/activity")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    sendJsonResponse(exchange, 200, toJsonActivityList(db.getRecentAdminActivity(actor.role())));
                } else if (path.equals("/api/admin/schedule")) {
                    AuthSession actor = findSession(exchange);
                    if (!requireRole(exchange, actor, "ADMIN")) return;
                    int year = Integer.parseInt(queryParams.getOrDefault("year", String.valueOf(java.time.Year.now().getValue())));
                    int month = Integer.parseInt(queryParams.getOrDefault("month", String.valueOf(java.time.LocalDate.now().getMonthValue())));
                    List<Integer> days = db.getAppointmentDays(year, month, actor.role());
                    String monthName = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
                    sendJsonResponse(exchange, 200, toJsonSchedule(year, month, monthName, days));
                } else if (path.equals("/api/db/status")) {
                    StringBuilder sb = new StringBuilder("{");
                    sb.append("\"connected\":").append(db.isConnectedToPostgres()).append(",");
                    sb.append("\"message\":\"").append(escapeJson(db.getConnectionStatusMessage())).append("\",");
                    sb.append("\"host\":\"").append(escapeJson(db.getDbHost())).append("\",");
                    sb.append("\"port\":").append(db.getDbPort()).append(",");
                    sb.append("\"dbName\":\"").append(escapeJson(db.getDbName())).append("\",");
                    sb.append("\"user\":\"").append(escapeJson(db.getDbUser())).append("\"");
                    sb.append("}");
                    sendJsonResponse(exchange, 200, sb.toString());
                } else if (path.equals("/api/db/reconnect")) {
                    Map<String, String> data = parseJson(body);
                    String host = data.getOrDefault("host", "localhost");
                    int port = Integer.parseInt(data.getOrDefault("port", "5432"));
                    String name = data.getOrDefault("name", "talkcare");
                    String user = data.getOrDefault("user", "postgres");
                    String pass = data.getOrDefault("password", "");
                    db.saveConfig(host, port, name, user, pass);
                    boolean ok = db.tryConnectAndMigrate();
                    sendJsonResponse(exchange, 200, "{\"connected\":" + ok + ",\"message\":\"" + escapeJson(db.getConnectionStatusMessage()) + "\"}");
                } else {
                    sendJsonResponse(exchange, 404, "{\"error\":\"Unknown endpoint: " + path + "\"}");
                }
            } catch (SecurityException e) {
                sendJsonResponse(exchange, 403, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private String saveBase64File(String base64Data, String prefix) {
        if (base64Data == null || !base64Data.contains(",")) return base64Data;
        try {
            String[] parts = base64Data.split(",", 2);
            String metadata = parts[0];
            String rawBase64 = parts[1];
            String ext = "png";
            if (metadata.contains("jpeg") || metadata.contains("jpg")) ext = "jpg";

            byte[] decoded = Base64.getDecoder().decode(rawBase64);
            String filename = prefix + "_" + System.currentTimeMillis() + "." + ext;
            File target = new File(uploadsDir, filename);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(decoded);
            }

            return "/uploads/" + filename;
        } catch (Exception e) {
            System.err.println("Error saving upload: " + e.getMessage());
            return "";
        }
    }

    private String saveChatAttachment(String attachmentData) {
        if (attachmentData == null || attachmentData.isBlank()) return "";

        String[] parts = attachmentData.split(",", 2);
        if (parts.length != 2
                || !(parts[0].equals("data:image/png;base64") || parts[0].equals("data:image/jpeg;base64"))
                || parts[1].length() > 7_000_000) {
            throw new IllegalArgumentException("Please attach a PNG or JPEG photo smaller than 5 MB.");
        }

        String attachmentUrl = saveBase64File(attachmentData, "chat");
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw new IllegalStateException("The photo could not be saved.");
        }
        return attachmentUrl;
    }

    private boolean isPasswordResetEmailConfigured() {
        return hasEnvironmentValue("TALKCARE_SMTP_HOST")
                && hasEnvironmentValue("TALKCARE_SMTP_USERNAME")
                && hasEnvironmentValue("TALKCARE_SMTP_PASSWORD")
                && hasEnvironmentValue("TALKCARE_SMTP_FROM");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String createSession(User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new AuthSession(user.getId(), user.getFullName(), user.getRole()));
        return token;
    }

    private AuthSession findSession(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Session-Token");
        return token == null || token.isBlank() ? null : sessions.get(token);
    }

    private boolean hasInvalidSessionToken(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Session-Token");
        return token != null && !token.isBlank() && !sessions.containsKey(token);
    }

    private boolean requireRole(HttpExchange exchange, AuthSession session, String... allowedRoles) throws IOException {
        if (session != null && Arrays.asList(allowedRoles).contains(session.role())) return true;
        sendJsonResponse(exchange, session == null ? 401 : 403,
                "{\"error\":\"You do not have permission to perform this action.\"}");
        return false;
    }

    private void requireConversationAccess(AuthSession actor, Conversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("The conversation could not be found.");
        }
        if (actor == null && conversation.getClientId() == 2) return;
        if (actor != null && "USER".equals(actor.role()) && conversation.getClientId() == actor.userId()) return;
        if (actor != null && ("THERAPIST".equals(actor.role()) || "CASE_HANDLER".equals(actor.role()))
                && conversation.getProfessionalId() != null
                && conversation.getProfessionalId() == actor.userId()) return;
        throw new SecurityException("You do not have access to this conversation.");
    }

    private boolean hasEnvironmentValue(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private void sendPasswordResetEmail(DatabaseManager.PasswordResetRequest resetRequest) throws Exception {
        String host = System.getenv("TALKCARE_SMTP_HOST");
        String username = System.getenv("TALKCARE_SMTP_USERNAME");
        String password = System.getenv("TALKCARE_SMTP_PASSWORD");
        String from = System.getenv("TALKCARE_SMTP_FROM");
        String port = System.getenv().getOrDefault("TALKCARE_SMTP_PORT", "587");
        String appUrl = System.getenv().getOrDefault("TALKCARE_RESET_URL", "http://localhost:" + this.port);
        while (appUrl.endsWith("/")) {
            appUrl = appUrl.substring(0, appUrl.length() - 1);
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "15000");
        properties.put("mail.smtp.timeout", "15000");
        properties.put("mail.smtp.writetimeout", "15000");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        String resetUrl = appUrl + "/?resetToken=" + resetRequest.token();
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(resetRequest.email(), false));
        message.setSubject("Reset your TalkCare password", StandardCharsets.UTF_8.name());
        message.setText(
                "A password reset was requested for your TalkCare account.\n\n"
                        + "Use this one-time link within 30 minutes:\n" + resetUrl
                        + "\n\nIf you did not request this, you can ignore this email.",
                StandardCharsets.UTF_8.name());
        Transport.send(message);
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString().trim());

        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon > 0) {
                String k = stripQuotes(token.substring(0, colon).trim());
                String v = stripQuotes(token.substring(colon + 1).trim());
                map.put(k, unescapeJson(v));
            }
        }
        return map;
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String toJson(User u) {
        if (u == null) return "null";
        return String.format(
                "{\"id\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"email\":\"%s\",\"contactNo\":\"%s\",\"address\":\"%s\",\"role\":\"%s\",\"profilePictureUrl\":\"%s\",\"idType\":\"%s\",\"idNumber\":\"%s\",\"idDocumentUrl\":\"%s\",\"status\":\"%s\"}",
                u.getId(), escapeJson(u.getUsername()), escapeJson(u.getFullName()), escapeJson(u.getEmail()),
                escapeJson(u.getContactNo()), escapeJson(u.getAddress()), escapeJson(u.getRole()),
                escapeJson(u.getProfilePictureUrl()), escapeJson(u.getIdType()),
                escapeJson(u.getIdNumber()), escapeJson(u.getIdDocumentUrl()), escapeJson(u.getStatus())
        );
    }

    private String toJsonSession(User user, String token) {
        String json = toJson(user);
        return json.substring(0, json.length() - 1)
                + ",\"sessionToken\":\"" + escapeJson(token) + "\"}";
    }

    private String toJson(Report r) {
        if (r == null) return "null";
        return String.format(
                "{\"id\":%d,\"reportCode\":\"%s\",\"userId\":%s,\"fullName\":\"%s\",\"email\":\"%s\",\"address\":\"%s\",\"contactNo\":\"%s\",\"concernType\":\"%s\",\"involvedWitness\":\"%s\",\"concernDetails\":\"%s\",\"status\":\"%s\",\"createdAt\":\"%s\"}",
                r.getId(), escapeJson(r.getReportCode()), r.getUserId() != null ? r.getUserId() : "null",
                escapeJson(r.getFullName()), escapeJson(r.getEmail()), escapeJson(r.getAddress()),
                escapeJson(r.getContactNo()), escapeJson(r.getConcernType()), escapeJson(r.getInvolvedWitness()),
                escapeJson(r.getConcernDetails()), escapeJson(r.getStatus()), r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
        );
    }

    private String toJson(Conversation c) {
        if (c == null) return "null";
        return String.format(
                "{\"id\":%d,\"clientId\":%d,\"professionalId\":%s,\"serviceType\":\"%s\",\"title\":\"%s\",\"createdAt\":\"%s\"}",
                c.getId(), c.getClientId(), c.getProfessionalId() != null ? c.getProfessionalId() : "null",
                escapeJson(c.getServiceType()), escapeJson(c.getTitle()),
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
        );
    }

    private String toJsonConversationList(List<Conversation> conversations) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < conversations.size(); i++) {
            if (i > 0) json.append(",");
            json.append(toJson(conversations.get(i)));
        }
        return json.append("]").toString();
    }

    private String toJson(ChatMessage m) {
        if (m == null) return "null";
        return String.format(
                "{\"id\":%d,\"conversationId\":%d,\"senderId\":%s,\"senderName\":\"%s\",\"senderRole\":\"%s\",\"messageText\":\"%s\",\"autoReply\":%b,\"attachmentUrl\":\"%s\",\"createdAt\":\"%s\"}",
                m.getId(), m.getConversationId(), m.getSenderId() != null ? m.getSenderId() : "null",
                escapeJson(m.getSenderName()), escapeJson(m.getSenderRole()), escapeJson(m.getMessageText()),
                m.isAutoReply(), escapeJson(m.getAttachmentUrl()), m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
        );
    }

    private String toJsonReportList(List<Report> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonAdminRequestList(List<DatabaseManager.AdminRequest> list) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            DatabaseManager.AdminRequest request = list.get(i);
            json.append(String.format(
                    "{\"type\":\"%s\",\"id\":%d,\"userId\":%s,\"name\":\"%s\",\"email\":\"%s\","
                            + "\"idType\":\"%s\",\"idNumber\":\"%s\",\"address\":\"%s\",\"contactNo\":\"%s\","
                            + "\"imageUrl\":\"%s\",\"status\":\"%s\",\"submittedAt\":\"%s\","
                            + "\"reportCode\":\"%s\",\"concernType\":\"%s\",\"details\":\"%s\","
                            + "\"witness\":\"%s\",\"remarks\":\"%s\"}",
                    escapeJson(request.type()), request.id(),
                    request.userId() == null ? "null" : request.userId().toString(),
                    escapeJson(request.name()), escapeJson(request.email()), escapeJson(request.idType()),
                    escapeJson(request.idNumber()), escapeJson(request.address()), escapeJson(request.contactNo()),
                    escapeJson(request.imageUrl()), escapeJson(request.status()), escapeJson(request.submittedAt()),
                    escapeJson(request.reportCode()), escapeJson(request.concernType()), escapeJson(request.details()),
                    escapeJson(request.witness()), escapeJson(request.remarks())));
        }
        return json.append("]").toString();
    }

    private String toJsonActivityList(List<DatabaseManager.ActivityEntry> list) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            DatabaseManager.ActivityEntry entry = list.get(i);
            json.append(String.format("{\"action\":\"%s\",\"details\":\"%s\",\"createdAt\":\"%s\"}",
                    escapeJson(entry.action()), escapeJson(entry.details()), escapeJson(entry.createdAt())));
        }
        return json.append("]").toString();
    }

    private String toJsonSchedule(int year, int month, String monthName, List<Integer> days) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"year\":").append(year).append(",\"month\":").append(month)
                .append(",\"monthName\":\"").append(escapeJson(monthName)).append("\",\"days\":[");
        for (int i = 0; i < days.size(); i++) {
            if (i > 0) json.append(",");
            json.append(days.get(i));
        }
        return json.append("]}").toString();
    }

    private String toJsonMessageList(List<ChatMessage> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonUserList(List<User> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
