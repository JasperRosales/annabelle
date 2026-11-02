package com.talkcare;

import com.talkcare.models.ChatMessage;
import com.talkcare.models.Conversation;
import com.talkcare.models.Report;
import com.talkcare.models.User;

import java.io.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {
    public record AdminRequest(String type, int id, Integer userId, String name, String email,
                               String idType, String idNumber, String address, String contactNo,
                               String imageUrl, String status, String submittedAt, String reportCode,
                               String concernType, String details, String witness, String remarks) {}

    public record ActivityEntry(String action, String details, String createdAt) {}

    private static DatabaseManager instance;

    private String dbHost = "localhost";
    private int dbPort = 5432;
    private String dbName = "talkcare";
    private String dbUser = "postgres";
    private String dbPassword = "postgres";

    private Connection connection;
    private boolean connectedToPostgres = false;
    private boolean legacyUsersSchema = false;
    private String connectionStatusMessage = "Not connected";

    // Resilient fallback storage in case PostgreSQL credentials are not yet entered
    private final List<User> memoryUsers = new CopyOnWriteArrayList<>();
    private final List<Report> memoryReports = new CopyOnWriteArrayList<>();
    private final List<Conversation> memoryConversations = new CopyOnWriteArrayList<>();
    private final List<ChatMessage> memoryMessages = new CopyOnWriteArrayList<>();
    private final Map<String, MemoryPasswordReset> memoryPasswordResets = new ConcurrentHashMap<>();

    private static final int PASSWORD_HASH_ITERATIONS = 210_000;
    private static final int PASSWORD_HASH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private record MemoryPasswordReset(int userId, long expiresAtMillis) {}

    public record PasswordResetRequest(String email, String token) {}

    private DatabaseManager() {
        loadConfig();
        initFallbackData();
        tryConnectAndMigrate();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void loadConfig() {
        File extFile = new File("talkcare_db.properties");
        Properties props = new Properties();

        if (extFile.exists()) {
            try (InputStream in = new FileInputStream(extFile)) {
                props.load(in);
            } catch (Exception e) {
                System.err.println("Could not load external config: " + e.getMessage());
            }
        } else {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception e) {
                System.err.println("Could not load bundled config: " + e.getMessage());
            }
        }

        dbHost = props.getProperty("db.host", dbHost);
        dbPort = Integer.parseInt(props.getProperty("db.port", String.valueOf(dbPort)));
        dbName = props.getProperty("db.name", dbName);
        dbUser = props.getProperty("db.user", dbUser);
        dbPassword = props.getProperty("db.password", dbPassword);
    }

    public synchronized void saveConfig(String host, int port, String name, String user, String pass) {
        this.dbHost = host;
        this.dbPort = port;
        this.dbName = name;
        this.dbUser = user;
        this.dbPassword = pass;

        Properties props = new Properties();
        props.setProperty("db.host", host);
        props.setProperty("db.port", String.valueOf(port));
        props.setProperty("db.name", name);
        props.setProperty("db.user", user);
        props.setProperty("db.password", pass);

        try (OutputStream out = new FileOutputStream("talkcare_db.properties")) {
            props.store(out, "TalkCare PostgreSQL Settings");
        } catch (Exception e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    public synchronized boolean tryConnectAndMigrate() {
        try {
            Class.forName("org.postgresql.Driver");

            // 1. Try connecting directly to target DB
            String targetUrl = String.format("jdbc:postgresql://%s:%d/%s", dbHost, dbPort, dbName);
            try {
                connection = DriverManager.getConnection(targetUrl, dbUser, dbPassword);
                connectedToPostgres = true;
                connectionStatusMessage = "Connected to PostgreSQL (" + targetUrl + ")";
                System.out.println(">>> " + connectionStatusMessage);
                if (!executeSchema()) return false;
                return true;
            } catch (SQLException e) {
                // If database does not exist (error 3D000 in postgres), try connecting to 'postgres' db and CREATE DATABASE
                System.out.println("Connecting to target database failed: " + e.getMessage());
                if (e.getMessage().contains("does not exist") || "3D000".equals(e.getSQLState())) {
                    try {
                        String rootUrl = String.format("jdbc:postgresql://%s:%d/postgres", dbHost, dbPort);
                        try (Connection rootConn = DriverManager.getConnection(rootUrl, dbUser, dbPassword);
                             Statement st = rootConn.createStatement()) {
                            st.executeUpdate("CREATE DATABASE " + dbName);
                            System.out.println("Created database " + dbName + " successfully!");
                        }
                        // Now connect to newly created DB
                        connection = DriverManager.getConnection(targetUrl, dbUser, dbPassword);
                        connectedToPostgres = true;
                        connectionStatusMessage = "Connected to PostgreSQL (" + targetUrl + ")";
                        if (!executeSchema()) return false;
                        return true;
                    } catch (Exception ex) {
                        System.err.println("Could not auto-create database: " + ex.getMessage());
                    }
                }
                connectedToPostgres = false;
                connectionStatusMessage = "PostgreSQL Error: " + e.getMessage();
                return false;
            }
        } catch (Exception e) {
            connectedToPostgres = false;
            connectionStatusMessage = "PostgreSQL Driver Error: " + e.getMessage();
            return false;
        }
    }

    private boolean executeSchema() {
        if (!connectedToPostgres || connection == null) return false;
        try {
            migrateUsersSchema();
        } catch (SQLException e) {
            connectedToPostgres = false;
            connectionStatusMessage = "PostgreSQL user schema migration failed: " + e.getMessage();
            System.err.println(connectionStatusMessage);
            return false;
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                connectionStatusMessage = "PostgreSQL schema.sql was not found.";
                return false;
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Split statements
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    if (legacyUsersSchema && trimmed.toUpperCase(Locale.ROOT).contains("INSERT INTO USERS")) {
                        continue;
                    }
                    try (PreparedStatement schemaStatement = connection.prepareStatement(trimmed)) {
                        schemaStatement.execute();
                    } catch (SQLException ex) {
                        // ignore benign notices
                        if (!ex.getMessage().contains("already exists")) {
                            System.err.println("Schema notice: " + ex.getMessage());
                        }
                    }
                }
            }
            try (PreparedStatement addReportCode = connection.prepareStatement(
                    "ALTER TABLE reports ADD COLUMN IF NOT EXISTS report_code VARCHAR(20) UNIQUE")) {
                addReportCode.execute();
            }
            try (PreparedStatement backfillReportCodes = connection.prepareStatement(
                    "UPDATE reports SET report_code = 'RPT-' || LPAD(id::text, 4, '0') WHERE report_code IS NULL")) {
                backfillReportCodes.executeUpdate();
            }
            try (PreparedStatement addMessageAttachment = connection.prepareStatement(
                    "ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_url TEXT")) {
                addMessageAttachment.execute();
            }
            try (PreparedStatement addRoleColumn = connection.prepareStatement(
                    "ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER'")) {
                addRoleColumn.execute();
            }
            try (PreparedStatement addReportRemarks = connection.prepareStatement(
                    "ALTER TABLE reports ADD COLUMN IF NOT EXISTS remarks TEXT")) {
                addReportRemarks.execute();
            }
            try (PreparedStatement createVerificationRequests = connection.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS verification_requests (
                        id SERIAL PRIMARY KEY,
                        user_id INT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                        id_type VARCHAR(100),
                        id_number VARCHAR(100),
                        address TEXT,
                        email VARCHAR(150),
                        id_document_url TEXT,
                        status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                        rejection_reason TEXT,
                        submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        reviewed_at TIMESTAMP,
                        reviewed_by INT REFERENCES users(id) ON DELETE SET NULL
                    )
                    """)) {
                createVerificationRequests.execute();
            }
            try (PreparedStatement createActivityLog = connection.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS activity_log (
                        id SERIAL PRIMARY KEY,
                        actor_user_id INT REFERENCES users(id) ON DELETE SET NULL,
                        affected_user_id INT REFERENCES users(id) ON DELETE SET NULL,
                        entity_type VARCHAR(40) NOT NULL,
                        entity_id INT NOT NULL,
                        action VARCHAR(80) NOT NULL,
                        details TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """)) {
                createActivityLog.execute();
            }
            try (PreparedStatement createNotifications = connection.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id SERIAL PRIMARY KEY,
                        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        message TEXT NOT NULL,
                        is_read BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """)) {
                createNotifications.execute();
            }
            try (PreparedStatement backfillVerificationRequests = connection.prepareStatement("""
                    INSERT INTO verification_requests
                        (user_id, id_type, id_number, address, email, id_document_url, status)
                    SELECT id, id_type, id_number, address, email, id_document_url, 'PENDING'
                    FROM users
                    WHERE status = 'PENDING_VERIFICATION'
                    ON CONFLICT (user_id) DO NOTHING
                    """)) {
                backfillVerificationRequests.executeUpdate();
            }
            try (PreparedStatement setRoleDefault = connection.prepareStatement(
                    "ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER'")) {
                setRoleDefault.execute();
            }
            try (PreparedStatement normalizeClientRoles = connection.prepareStatement(
                    "UPDATE users SET role = 'USER' WHERE role IS NULL OR role IN ('CLIENT', 'PEER')")) {
                normalizeClientRoles.executeUpdate();
            }
            try (PreparedStatement normalizeProfessionalRoles = connection.prepareStatement(
                    "UPDATE users SET role = 'THERAPIST' WHERE role = 'PROFESSIONAL'")) {
                normalizeProfessionalRoles.executeUpdate();
            }
            try (PreparedStatement assignCaseHandlerRole = connection.prepareStatement(
                    "UPDATE users SET role = 'CASE_HANDLER' WHERE LOWER(username) = 'sofiaramos' OR LOWER(email) = 'sofia.ramos@talkcare.com'")) {
                assignCaseHandlerRole.executeUpdate();
            }
            seedRoleTestAccounts();
            System.out.println("TalkCare database schema verified and initialized!");
            return true;
        } catch (Exception e) {
            System.err.println("Error executing schema: " + e.getMessage());
            connectionStatusMessage = "PostgreSQL schema initialization failed: " + e.getMessage();
            return false;
        }
    }

    private void seedRoleTestAccounts() throws SQLException {
        seedRoleTestAccount("test-user", "TalkCare Test User", "test.user@talkcare.com",
                "TalkCareUser#2026", "USER");
        seedRoleTestAccount("test-therapist", "TalkCare Test Therapist", "test.therapist@talkcare.com",
                "TalkCareTherapist#2026", "THERAPIST");
        seedRoleTestAccount("test-case-handler", "TalkCare Test Case Handler", "test.casehandler@talkcare.com",
                "TalkCareCaseHandler#2026", "CASE_HANDLER");
        seedRoleTestAccount("test-admin", "TalkCare Test Admin", "test.admin@talkcare.com",
                "TalkCareAdmin#2026", "ADMIN");
    }

    private void seedRoleTestAccount(String username, String fullName, String email,
                                     String password, String role) throws SQLException {
        String checkSql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)";
        try (PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setString(1, username);
            check.setString(2, email);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return;
            }
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        String insertSql = legacyUsersSchema
                ? "INSERT INTO users (username, full_name, email, password, contact_no, address, role, status, name, password_hash, contact) VALUES (?, ?, ?, ?, '', '', ?, 'ACTIVE', ?, ?, '') ON CONFLICT DO NOTHING"
                : "INSERT INTO users (username, full_name, email, password, contact_no, address, role, status) VALUES (?, ?, ?, ?, '', '', ?, 'ACTIVE') ON CONFLICT DO NOTHING";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, username);
            insert.setString(2, fullName);
            insert.setString(3, email);
            insert.setString(4, passwordHash);
            insert.setString(5, role);
            if (legacyUsersSchema) {
                insert.setString(6, fullName);
                insert.setString(7, passwordHash);
            }
            insert.executeUpdate();
        }
    }

    private void migrateUsersSchema() throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, "public", "users", new String[]{"TABLE"})) {
            if (!tables.next()) {
                legacyUsersSchema = false;
                return;
            }
        }
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = connection.getMetaData().getColumns(null, "public", "users", null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        legacyUsersSchema = columns.contains("name") && columns.contains("password_hash")
                && columns.contains("contact");

        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(100)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(150)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_no VARCHAR(50)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_picture_url TEXT");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS id_type VARCHAR(100)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS id_number VARCHAR(100)");
            st.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS id_document_url TEXT");

            if (legacyUsersSchema) {
                st.executeUpdate(
                        "UPDATE users SET full_name = COALESCE(NULLIF(full_name, ''), name), "
                                + "password = COALESCE(NULLIF(password, ''), password_hash), "
                                + "contact_no = COALESCE(NULLIF(contact_no, ''), contact), "
                                + "profile_picture_url = COALESCE(profile_picture_url, avatar), "
                                + "username = COALESCE(NULLIF(username, ''), "
                                + "LEFT(NULLIF(REGEXP_REPLACE(LOWER(SPLIT_PART(email, '@', 1)), '[^a-z0-9._-]', '', 'g'), '') , 80) "
                                + "|| '_' || id::text) "
                                + "WHERE username IS NULL OR username = '' OR full_name IS NULL "
                                + "OR password IS NULL OR contact_no IS NULL");
                st.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
                st.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK "
                        + "(role IN ('USER', 'CLIENT', 'PEER', 'THERAPIST', 'CASE_HANDLER', 'PROFESSIONAL', 'ADMIN'))");
                st.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check");
                st.execute("ALTER TABLE users ADD CONSTRAINT users_status_check CHECK "
                        + "(status IN ('ACTIVE', 'UNVERIFIED', 'PENDING', 'PENDING_VERIFICATION', 'VERIFIED', 'REJECTED'))");
            } else {
                st.executeUpdate("UPDATE users SET contact_no = COALESCE(contact_no, '') WHERE contact_no IS NULL");
            }
            st.execute("ALTER TABLE users ALTER COLUMN username SET NOT NULL");
            st.execute("ALTER TABLE users ALTER COLUMN full_name SET NOT NULL");
            st.execute("ALTER TABLE users ALTER COLUMN password SET NOT NULL");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_username_key ON users (username)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_key ON users (LOWER(username))");
        }
    }

    private void initFallbackData() {
        // Seed users
        User admin = new User(1, "admin", "Admin User", "admin@talkcare.com", "admin123", "09123456789", "TalkCare HQ, Manila", "ADMIN", "", "Passport", "P1234567A", "", "VERIFIED");
        User max = new User(2, "maxsmith", "Max Smith", "max.smith@talkcare.com", "max123", "096364664286", "Quezon City, Philippines", "USER", "", "Student ID", "2024-00123", "", "VERIFIED");
        User sofia = new User(3, "sofiaramos", "Atty. Sofia Ramos", "sofia.ramos@talkcare.com", "justice123", "09171234567", "JusticeCare Center, Manila", "CASE_HANDLER", "", "PRC ID", "PRC-987654", "", "VERIFIED");
        User eleanor = new User(4, "eleanorreyes", "Dr. Eleanor Reyes", "eleanor.reyes@talkcare.com", "therapist123", "09189876543", "ThoMatt Mental Health Clinic", "THERAPIST", "", "PRC ID", "PRC-112233", "", "VERIFIED");

        memoryUsers.add(admin);
        memoryUsers.add(max);
        memoryUsers.add(sofia);
        memoryUsers.add(eleanor);

        // Seed sample reports matching PDF
        Report r1 = new Report();
        r1.setId(1);
        r1.setReportCode("Report #1");
        r1.setFullName("Max Smith");
        r1.setEmail("max.smith@talkcare.com");
        r1.setAddress("Quezon City");
        r1.setContactNo("096364664286");
        r1.setConcernType("Domestic Injustice");
        r1.setInvolvedWitness("Family counselor");
        r1.setConcernDetails("Client experienced unfair family dispute and sought immediate legal advice and counseling.");
        r1.setStatus("Resolved");
        r1.setCreatedAt(new Timestamp(System.currentTimeMillis() - 3L * 86400000L));
        memoryReports.add(r1);

        Report r2 = new Report();
        r2.setId(2);
        r2.setReportCode("Report #2");
        r2.setFullName("Alex Taylor");
        r2.setEmail("alex.t@example.com");
        r2.setAddress("Batangas City");
        r2.setContactNo("09281234567");
        r2.setConcernType("Workplace Discrimination");
        r2.setInvolvedWitness("Coworker Maria");
        r2.setConcernDetails("Unfair treatment and severe workplace pressure causing acute anxiety and distress.");
        r2.setStatus("In Progress");
        r2.setCreatedAt(new Timestamp(System.currentTimeMillis() - 1L * 86400000L));
        memoryReports.add(r2);

        Report r3 = new Report();
        r3.setId(3);
        r3.setReportCode("Report #3");
        r3.setFullName("Jordan Lee");
        r3.setEmail("jordan.lee@example.com");
        r3.setAddress("Manila");
        r3.setContactNo("09391234567");
        r3.setConcernType("Student Mental Distress");
        r3.setInvolvedWitness("School Guidance Counselor");
        r3.setConcernDetails("Academic overload and severe anxiety needing confidential psychological consultation.");
        r3.setStatus("Under Review");
        r3.setCreatedAt(new Timestamp(System.currentTimeMillis() - 4L * 3600000L));
        memoryReports.add(r3);

        // Seed conversations
        Conversation c1 = new Conversation();
        c1.setId(1);
        c1.setClientId(2);
        c1.setProfessionalId(3);
        c1.setServiceType("JUSTICE_CARE");
        c1.setTitle("JusticeCare Legal Protection & Rights Support");
        c1.setCreatedAt(new Timestamp(System.currentTimeMillis() - 7200000L));
        memoryConversations.add(c1);

        ChatMessage m1 = new ChatMessage();
        m1.setId(1);
        m1.setConversationId(1);
        m1.setSenderId(3);
        m1.setSenderName("Atty. Sofia Ramos");
        m1.setSenderRole("PROFESSIONAL");
        m1.setMessageText("Hello, I am Atty. Sofia Ramos from JusticeCare. How can we support and protect your rights today?");
        m1.setAutoReply(false);
        m1.setCreatedAt(new Timestamp(System.currentTimeMillis() - 7100000L));
        memoryMessages.add(m1);

        Conversation c2 = new Conversation();
        c2.setId(2);
        c2.setClientId(2);
        c2.setProfessionalId(4);
        c2.setServiceType("THOMATT");
        c2.setTitle("ThoMatt Anonymous Mental Consultation");
        c2.setCreatedAt(new Timestamp(System.currentTimeMillis() - 7200000L));
        memoryConversations.add(c2);

        ChatMessage m2 = new ChatMessage();
        m2.setId(2);
        m2.setConversationId(2);
        m2.setSenderId(4);
        m2.setSenderName("Dr. Eleanor Reyes");
        m2.setSenderRole("PROFESSIONAL");
        m2.setMessageText("Welcome to ThoMatt. I am Dr. Eleanor Reyes (R.Psy / L.P.T.). This is a safe and confidential environment. Tell me your thoughts...");
        m2.setAutoReply(false);
        m2.setCreatedAt(new Timestamp(System.currentTimeMillis() - 7000000L));
        memoryMessages.add(m2);
    }

    public boolean isConnectedToPostgres() {
        return connectedToPostgres;
    }

    public String getConnectionStatusMessage() {
        return connectionStatusMessage;
    }

    public String getDbHost() { return dbHost; }
    public int getDbPort() { return dbPort; }
    public String getDbName() { return dbName; }
    public String getDbUser() { return dbUser; }

    // Authentication
    public User login(String loginId, String password) throws SQLException {
        if (loginId == null || loginId.isBlank() || password == null || password.isEmpty()) {
            return null;
        }

        if (connectedToPostgres && connection != null) {
            String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) OR LOWER(full_name) = LOWER(?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, loginId);
                ps.setString(2, loginId);
                ps.setString(3, loginId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        User user = mapUser(rs);
                        if (verifyPassword(user, password)) {
                            if (!isPasswordHash(user.getPassword()) && !isBcryptPasswordHash(user.getPassword())) {
                                try {
                                    upgradePasswordHash(user, password);
                                } catch (SQLException e) {
                                    System.err.println("Could not upgrade account password hash: " + e.getMessage());
                                }
                            }
                            return user;
                        }
                    }
                }
            } catch (SQLException e) {
                throw e;
            } catch (GeneralSecurityException e) {
                throw new SQLException("Could not verify account password.", e);
            }
        }
        for (User u : memoryUsers) {
            if ((equalsIgnoreCase(u.getUsername(), loginId)
                    || equalsIgnoreCase(u.getEmail(), loginId)
                    || equalsIgnoreCase(u.getFullName(), loginId))
                    && verifyPasswordQuietly(u, password)) {
                if (!isPasswordHash(u.getPassword()) && !isBcryptPasswordHash(u.getPassword())) {
                    u.setPassword(hashPassword(password));
                }
                return u;
            }
        }
        return null;
    }

    public User register(String username, String fullName, String email, String password, String contactNo, String address) throws SQLException {
        if (connectedToPostgres && connection != null) {
            String sql = legacyUsersSchema
                    ? "INSERT INTO users (username, full_name, email, password, contact_no, address, role, status, name, password_hash, contact) VALUES (?, ?, ?, ?, ?, ?, 'USER', 'UNVERIFIED', ?, ?, ?) RETURNING *"
                    : "INSERT INTO users (username, full_name, email, password, contact_no, address, role, status) VALUES (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE') RETURNING *";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                String hashedPassword = hashPassword(password);
                ps.setString(1, username);
                ps.setString(2, fullName);
                ps.setString(3, email);
                ps.setString(4, hashedPassword);
                ps.setString(5, contactNo);
                ps.setString(6, address);
                if (legacyUsersSchema) {
                    ps.setString(7, fullName);
                    ps.setString(8, BCrypt.hashpw(password, BCrypt.gensalt()));
                    ps.setString(9, contactNo);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapUser(rs);
                    }
                }
            }
            return null;
        }
        User newUser = new User(memoryUsers.size() + 1, username, fullName, email, hashPassword(password), contactNo, address, "USER", "", "", "", "", "ACTIVE");
        memoryUsers.add(newUser);
        return newUser;
    }

    private boolean equalsIgnoreCase(String value, String other) {
        return value != null && value.equalsIgnoreCase(other);
    }

    private boolean verifyPasswordQuietly(User user, String password) {
        try {
            return verifyPassword(user, password);
        } catch (GeneralSecurityException e) {
            System.err.println("Could not verify account password: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyPassword(User user, String password) throws GeneralSecurityException {
        String stored = user.getPassword();
        if (!isPasswordHash(stored)) {
            if (stored != null && stored.matches("^\\$2[aby]\\$\\d{2}\\$.*")) {
                return BCrypt.checkpw(password, stored);
            }
            return stored != null && MessageDigest.isEqual(
                    stored.getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8));
        }

        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derivePasswordHash(password, salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    private boolean isPasswordHash(String value) {
        return value != null && value.startsWith("pbkdf2-sha256$");
    }

    private boolean isBcryptPasswordHash(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.*");
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        try {
            byte[] hash = derivePasswordHash(password, salt, PASSWORD_HASH_ITERATIONS, PASSWORD_HASH_BITS);
            return "pbkdf2-sha256$" + PASSWORD_HASH_ITERATIONS + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
                    + Base64.getEncoder().withoutPadding().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not securely hash the password.", e);
        }
    }

    private byte[] derivePasswordHash(String password, byte[] salt, int iterations, int bits)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private void upgradePasswordHash(User user, String password) throws SQLException {
        String hashedPassword = hashPassword(password);
        String sql = legacyUsersSchema
                ? "UPDATE users SET password = ?, password_hash = ? WHERE id = ?"
                : "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, hashedPassword);
            if (legacyUsersSchema) {
                ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
                ps.setInt(3, user.getId());
            } else {
                ps.setInt(2, user.getId());
            }
            ps.executeUpdate();
            user.setPassword(hashedPassword);
        }
    }

    public synchronized PasswordResetRequest createPasswordResetRequest(String email) throws SQLException {
        if (email == null || email.isBlank()) {
            return null;
        }

        String token = newResetToken();
        String tokenHash = hashResetToken(token);
        if (connectedToPostgres && connection != null) {
            try (Connection resetConnection = openDatabaseConnection()) {
                String findUserSql = "SELECT id, email FROM users WHERE LOWER(email) = LOWER(?)";
                int userId;
                String accountEmail;
                try (PreparedStatement ps = resetConnection.prepareStatement(findUserSql)) {
                    ps.setString(1, email.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        userId = rs.getInt("id");
                        accountEmail = rs.getString("email");
                    }
                }
                try {
                    resetConnection.setAutoCommit(false);
                    try (PreparedStatement delete = resetConnection.prepareStatement(
                            "DELETE FROM password_reset_tokens WHERE user_id = ?")) {
                        delete.setInt(1, userId);
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = resetConnection.prepareStatement(
                            "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '30 minutes')")) {
                        insert.setInt(1, userId);
                        insert.setString(2, tokenHash);
                        insert.executeUpdate();
                    }
                    resetConnection.commit();
                    return new PasswordResetRequest(accountEmail, token);
                } catch (SQLException e) {
                    resetConnection.rollback();
                    throw e;
                } finally {
                    resetConnection.setAutoCommit(true);
                }
            }
        }

        for (User user : memoryUsers) {
            if (equalsIgnoreCase(user.getEmail(), email.trim())) {
                memoryPasswordResets.entrySet().removeIf(entry -> entry.getValue().userId() == user.getId());
                memoryPasswordResets.put(tokenHash,
                        new MemoryPasswordReset(user.getId(), System.currentTimeMillis() + 30 * 60 * 1000L));
                return new PasswordResetRequest(user.getEmail(), token);
            }
        }
        return null;
    }

    public synchronized boolean resetPassword(String token, String newPassword) throws SQLException {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        String tokenHash = hashResetToken(token);
        String hashedPassword = hashPassword(newPassword);

        if (connectedToPostgres && connection != null) {
            try (Connection resetConnection = openDatabaseConnection()) {
                resetConnection.setAutoCommit(false);
                int userId;
                try (PreparedStatement select = resetConnection.prepareStatement(
                        "SELECT user_id FROM password_reset_tokens WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP AND used_at IS NULL FOR UPDATE")) {
                    select.setString(1, tokenHash);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            resetConnection.rollback();
                            return false;
                        }
                        userId = rs.getInt("user_id");
                    }
                }
                String updateSql = legacyUsersSchema
                        ? "UPDATE users SET password = ?, password_hash = ? WHERE id = ?"
                        : "UPDATE users SET password = ? WHERE id = ?";
                try (PreparedStatement update = resetConnection.prepareStatement(updateSql)) {
                    update.setString(1, hashedPassword);
                    if (legacyUsersSchema) {
                        update.setString(2, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
                        update.setInt(3, userId);
                    } else {
                        update.setInt(2, userId);
                    }
                    if (update.executeUpdate() != 1) {
                        resetConnection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement consume = resetConnection.prepareStatement(
                        "UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP WHERE token_hash = ?")) {
                    consume.setString(1, tokenHash);
                    consume.executeUpdate();
                }
                resetConnection.commit();
                return true;
            } catch (SQLException e) {
                try (Connection resetConnection = openDatabaseConnection();
                     PreparedStatement delete = resetConnection.prepareStatement(
                             "DELETE FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL")) {
                    delete.setString(1, tokenHash);
                    delete.executeUpdate();
                } catch (SQLException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
                throw e;
            }
        }

        MemoryPasswordReset reset = memoryPasswordResets.remove(tokenHash);
        if (reset == null || reset.expiresAtMillis() <= System.currentTimeMillis()) {
            return false;
        }

        for (User user : memoryUsers) {
            if (user.getId() == reset.userId()) {
                user.setPassword(hashedPassword);
                return true;
            }
        }
        return false;
    }

    private Connection openDatabaseConnection() throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s", dbHost, dbPort, dbName);
        return DriverManager.getConnection(url, dbUser, dbPassword);
    }

    public synchronized void invalidatePasswordResetToken(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return;
        }
        String tokenHash = hashResetToken(token);
        memoryPasswordResets.remove(tokenHash);
        if (connectedToPostgres && connection != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM password_reset_tokens WHERE token_hash = ? AND used_at IS NULL")) {
                ps.setString(1, tokenHash);
                ps.executeUpdate();
            }
        }
    }

    private String newResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashResetToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    public User getUserById(int id) {
        if (connectedToPostgres && connection != null) {
            String sql = "SELECT * FROM users WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapUser(rs);
                    }
                }
            } catch (SQLException e) {
                System.err.println("DB getUserById error: " + e.getMessage());
            }
        }
        for (User u : memoryUsers) {
            if (u.getId() == id) return u;
        }
        return null;
    }

    public boolean updateProfile(int userId, String username, String email, String password, String profilePicUrl) {
        String passwordHash = password != null && !password.isEmpty() ? hashPassword(password) : null;
        if (connectedToPostgres && connection != null) {
            String sql = legacyUsersSchema
                    ? "UPDATE users SET username = COALESCE(NULLIF(?, ''), username), email = COALESCE(NULLIF(?, ''), email), password = COALESCE(NULLIF(?, ''), password), profile_picture_url = COALESCE(NULLIF(?, ''), profile_picture_url), password_hash = COALESCE(NULLIF(?, ''), password_hash), avatar = COALESCE(NULLIF(?, ''), avatar) WHERE id = ?"
                    : "UPDATE users SET username = COALESCE(NULLIF(?, ''), username), email = COALESCE(NULLIF(?, ''), email), password = COALESCE(NULLIF(?, ''), password), profile_picture_url = COALESCE(NULLIF(?, ''), profile_picture_url) WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, email);
                ps.setString(3, passwordHash);
                ps.setString(4, profilePicUrl);
                if (legacyUsersSchema) {
                    ps.setString(5, password != null && !password.isEmpty()
                            ? BCrypt.hashpw(password, BCrypt.gensalt()) : null);
                    ps.setString(6, profilePicUrl);
                    ps.setInt(7, userId);
                } else {
                    ps.setInt(5, userId);
                }
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("DB updateProfile error: " + e.getMessage());
            }
        }
        for (User u : memoryUsers) {
            if (u.getId() == userId) {
                if (username != null && !username.trim().isEmpty()) u.setUsername(username);
                if (email != null && !email.trim().isEmpty()) u.setEmail(email);
                if (passwordHash != null) u.setPassword(passwordHash);
                if (profilePicUrl != null && !profilePicUrl.trim().isEmpty()) u.setProfilePictureUrl(profilePicUrl);
                return true;
            }
        }
        return false;
    }

    public boolean updateIdVerification(int userId, String idType, String idNumber, String address, String email, String idDocUrl) {
        if (connectedToPostgres && connection != null) {
            boolean previousAutoCommit = true;
            try {
                previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                String updateUser = "UPDATE users SET id_type = ?, id_number = ?, address = ?, email = ?, id_document_url = ?, status = 'PENDING_VERIFICATION' WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(updateUser)) {
                    ps.setString(1, idType);
                    ps.setString(2, idNumber);
                    ps.setString(3, address);
                    ps.setString(4, email);
                    ps.setString(5, idDocUrl);
                    ps.setInt(6, userId);
                    if (ps.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                String upsertRequest = """
                        INSERT INTO verification_requests
                            (user_id, id_type, id_number, address, email, id_document_url, status,
                             rejection_reason, submitted_at, reviewed_at, reviewed_by)
                        VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NULL, CURRENT_TIMESTAMP, NULL, NULL)
                        ON CONFLICT (user_id) DO UPDATE SET
                            id_type = EXCLUDED.id_type,
                            id_number = EXCLUDED.id_number,
                            address = EXCLUDED.address,
                            email = EXCLUDED.email,
                            id_document_url = EXCLUDED.id_document_url,
                            status = 'PENDING',
                            rejection_reason = NULL,
                            submitted_at = CURRENT_TIMESTAMP,
                            reviewed_at = NULL,
                            reviewed_by = NULL
                        """;
                try (PreparedStatement ps = connection.prepareStatement(upsertRequest)) {
                    ps.setInt(1, userId);
                    ps.setString(2, idType);
                    ps.setString(3, idNumber);
                    ps.setString(4, address);
                    ps.setString(5, email);
                    ps.setString(6, idDocUrl);
                    ps.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                System.err.println("DB updateIdVerification error: " + e.getMessage());
                return false;
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException e) {
                    System.err.println("Could not restore database transaction mode: " + e.getMessage());
                }
            }
        }
        for (User u : memoryUsers) {
            if (u.getId() == userId) {
                u.setIdType(idType);
                u.setIdNumber(idNumber);
                u.setAddress(address);
                u.setEmail(email);
                u.setIdDocumentUrl(idDocUrl);
                u.setStatus("PENDING_VERIFICATION");
                return true;
            }
        }
        return false;
    }

    // Reports
    public Report submitReport(Report report, Integer actorId, String actorRole) {
        if (actorRole != null && !"USER".equals(actorRole)) {
            throw new SecurityException("Only a USER account can submit a client report.");
        }
        if (actorId != null) {
            report.setUserId(actorId);
        }
        report.setStatus("Under Review");
        report.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        if (connectedToPostgres && connection != null) {
            String sql = """
                    WITH next_report_id AS (
                        SELECT nextval(pg_get_serial_sequence('reports', 'id')) AS id
                    )
                    INSERT INTO reports (id, report_code, user_id, full_name, email, address, contact_no, concern_type, involved_witness, concern_details, status)
                    SELECT id, 'RPT-' || LPAD(id::text, GREATEST(4, LENGTH(id::text)), '0'), ?, ?, ?, ?, ?, ?, ?, ?, ?
                    FROM next_report_id
                    RETURNING id, report_code
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (report.getUserId() != null) ps.setInt(1, report.getUserId()); else ps.setNull(1, Types.INTEGER);
                ps.setString(2, report.getFullName());
                ps.setString(3, report.getEmail());
                ps.setString(4, report.getAddress());
                ps.setString(5, report.getContactNo());
                ps.setString(6, report.getConcernType());
                ps.setString(7, report.getInvolvedWitness());
                ps.setString(8, report.getConcernDetails());
                ps.setString(9, report.getStatus());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        report.setId(rs.getInt(1));
                        report.setReportCode(rs.getString(2));
                        return report;
                    }
                }
                throw new IllegalStateException("PostgreSQL did not confirm the report was saved.");
            } catch (SQLException e) {
                throw new IllegalStateException("The report could not be saved to PostgreSQL.", e);
            }
        }
        report.setId(memoryReports.size() + 1);
        report.setReportCode(String.format(Locale.ROOT, "RPT-%04d", report.getId()));
        memoryReports.add(0, report);
        return report;
    }

    public List<Report> getReports(String filter, int actorId, String actorRole) {
        boolean isAdmin = "ADMIN".equals(actorRole);
        if (!isAdmin && !"USER".equals(actorRole)) {
            throw new SecurityException("Only USER or ADMIN accounts can read reports.");
        }

        List<Report> list = new ArrayList<>();
        if (connectedToPostgres && connection != null) {
            String sql = "SELECT id, report_code, user_id, full_name, email, address, contact_no, concern_type, involved_witness, concern_details, status, created_at FROM reports";
            if (!isAdmin) {
                sql += " WHERE user_id = ?";
            }
            if (filter != null && !filter.equalsIgnoreCase("All")) {
                sql += isAdmin ? " WHERE status ILIKE ?" : " AND status ILIKE ?";
            }
            sql += " ORDER BY id DESC";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int parameterIndex = 1;
                if (!isAdmin) {
                    ps.setInt(parameterIndex++, actorId);
                }
                if (filter != null && !filter.equalsIgnoreCase("All")) {
                    ps.setString(parameterIndex, "%" + filter + "%");
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapReport(rs));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load reports from PostgreSQL.", e);
            }
        }

        for (Report r : memoryReports) {
            boolean ownsReport = isAdmin || (r.getUserId() != null && r.getUserId() == actorId);
            if (ownsReport && (filter == null || filter.equalsIgnoreCase("All") || r.getStatus().equalsIgnoreCase(filter))) {
                list.add(r);
            }
        }
        return list;
    }

    public synchronized List<AdminRequest> getAdminPendingRequests(String actorRole) {
        requireAdmin(actorRole);
        requirePostgres();
        List<AdminRequest> requests = new ArrayList<>();

        String verificationSql = """
                SELECT vr.id, u.id AS user_id, u.full_name, u.email, vr.id_type, vr.id_number,
                       vr.address, u.contact_no, vr.id_document_url, vr.status, vr.submitted_at
                FROM verification_requests vr
                JOIN users u ON u.id = vr.user_id
                WHERE u.status = 'PENDING_VERIFICATION' AND vr.status = 'PENDING'
                ORDER BY vr.submitted_at DESC
                """;
        try (PreparedStatement ps = connection.prepareStatement(verificationSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(new AdminRequest(
                        "VERIFICATION", rs.getInt("id"), rs.getInt("user_id"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("id_type"),
                        rs.getString("id_number"), rs.getString("address"), rs.getString("contact_no"),
                        rs.getString("id_document_url"), rs.getString("status"),
                        timestampText(rs.getTimestamp("submitted_at")), null, null, null, null, null));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load pending verification requests.", e);
        }

        String reportsSql = """
                SELECT r.id, r.user_id, r.full_name, r.email, r.address, r.contact_no, r.status,
                       r.created_at, r.report_code, r.concern_type, r.concern_details,
                       r.involved_witness, r.remarks
                FROM reports r
                WHERE UPPER(REPLACE(r.status, ' ', '_')) = 'UNDER_REVIEW'
                ORDER BY r.created_at DESC
                """;
        try (PreparedStatement ps = connection.prepareStatement(reportsSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int userIdValue = rs.getInt("user_id");
                Integer userId = rs.wasNull() ? null : userIdValue;
                requests.add(new AdminRequest(
                        "REPORT", rs.getInt("id"), userId, rs.getString("full_name"),
                        rs.getString("email"), null, null, rs.getString("address"),
                        rs.getString("contact_no"), null, rs.getString("status"),
                        timestampText(rs.getTimestamp("created_at")), rs.getString("report_code"),
                        rs.getString("concern_type"), rs.getString("concern_details"),
                        rs.getString("involved_witness"), rs.getString("remarks")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load pending concern reports.", e);
        }

        requests.sort(Comparator.comparing(AdminRequest::submittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return requests;
    }

    public synchronized void processAdminRequest(int id, String type, String action, String reason,
                                                 String remarks, int actorId, String actorRole) {
        requireAdmin(actorRole);
        requirePostgres();
        boolean verification = "VERIFICATION".equals(type);
        boolean report = "REPORT".equals(type);
        if (!verification && !report) throw new IllegalArgumentException("Unknown request type.");
        if (action == null) throw new IllegalArgumentException("Choose an action.");
        if (verification && !Set.of("APPROVE", "REJECT").contains(action)) {
            throw new IllegalArgumentException("Invalid verification action.");
        }
        if (report && !Set.of("IN_PROGRESS", "RESOLVED", "ADD_REMARKS").contains(action)) {
            throw new IllegalArgumentException("Invalid report action.");
        }
        if ("REJECT".equals(action) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A rejection reason is required.");
        }
        if ("ADD_REMARKS".equals(action) && (remarks == null || remarks.isBlank())) {
            throw new IllegalArgumentException("Please enter remarks before saving.");
        }

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int affectedUserId;
            String subject;
            String actionLabel;
            String notification;
            if (verification) {
                String selectSql = """
                        SELECT vr.user_id, u.full_name
                        FROM verification_requests vr
                        JOIN users u ON u.id = vr.user_id
                        WHERE vr.id = ? AND vr.status = 'PENDING'
                          AND u.status = 'PENDING_VERIFICATION'
                        FOR UPDATE OF vr, u
                        """;
                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("This verification request is no longer pending.");
                        affectedUserId = rs.getInt("user_id");
                        subject = rs.getString("full_name");
                    }
                }

                String newStatus = "APPROVE".equals(action) ? "VERIFIED" : "REJECTED";
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE users SET status = ? WHERE id = ? AND status = 'PENDING_VERIFICATION'")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, affectedUserId);
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("The user request has already been reviewed.");
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE verification_requests
                        SET status = ?, rejection_reason = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?
                        WHERE id = ? AND status = 'PENDING'
                        """)) {
                    ps.setString(1, newStatus);
                    ps.setString(2, "REJECT".equals(action) ? reason.trim() : null);
                    ps.setInt(3, actorId);
                    ps.setInt(4, id);
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("The verification request has already been reviewed.");
                }
                actionLabel = "APPROVE".equals(action) ? "Verification approved" : "Verification rejected";
                subject = subject == null ? "User" : subject;
                notification = "APPROVE".equals(action)
                        ? "Your account verification has been approved."
                        : "Your account verification was rejected: " + reason.trim();
            } else {
                String selectSql = """
                        SELECT user_id, full_name, email, report_code
                        FROM reports WHERE id = ? AND UPPER(REPLACE(status, ' ', '_')) = 'UNDER_REVIEW'
                        FOR UPDATE
                        """;
                String email;
                String reportCode;
                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("This report is no longer pending.");
                        int userIdValue = rs.getInt("user_id");
                        affectedUserId = rs.wasNull() ? 0 : userIdValue;
                        subject = rs.getString("full_name");
                        email = rs.getString("email");
                        reportCode = rs.getString("report_code");
                    }
                }
                if (affectedUserId == 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT id FROM users WHERE LOWER(email) = LOWER(?)")) {
                        ps.setString(1, email);
                        try (ResultSet rs = ps.executeQuery()) {
                            affectedUserId = rs.next() ? rs.getInt("id") : 0;
                        }
                    }
                }

                if ("ADD_REMARKS".equals(action)) {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE reports SET remarks =
                                CASE WHEN COALESCE(remarks, '') = '' THEN ? ELSE remarks || E'\\n' || ? END,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND UPPER(REPLACE(status, ' ', '_')) = 'UNDER_REVIEW'
                            """)) {
                        ps.setString(1, remarks.trim());
                        ps.setString(2, remarks.trim());
                        ps.setInt(3, id);
                        if (ps.executeUpdate() != 1) throw new IllegalArgumentException("The report is no longer pending.");
                    }
                    actionLabel = "Remarks added";
                    notification = "A TalkCare admin added remarks to your report " + reportCode + ".";
                } else {
                    String newStatus = "IN_PROGRESS".equals(action) ? "In Progress" : "Resolved";
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE reports SET status = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND UPPER(REPLACE(status, ' ', '_')) = 'UNDER_REVIEW'
                            """)) {
                        ps.setString(1, newStatus);
                        ps.setInt(2, id);
                        if (ps.executeUpdate() != 1) throw new IllegalArgumentException("The report is no longer pending.");
                    }
                    actionLabel = "Report marked " + newStatus;
                    notification = "Your report " + reportCode + " is now marked " + newStatus + ".";
                }
                subject = (subject == null ? "Client" : subject) + " - " + reportCode;
            }

            String details = actionLabel + " for " + subject
                    + ("REJECT".equals(action) ? ". Reason: " + reason.trim() : "")
                    + ("ADD_REMARKS".equals(action) ? ". Remarks: " + remarks.trim() : "");
            insertActivity(actorId, verification ? affectedUserId : (affectedUserId == 0 ? null : affectedUserId),
                    type, id, action, details);
            if (affectedUserId != 0) insertNotification(affectedUserId, notification);
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw new IllegalStateException("The admin action could not be saved.", e);
        } catch (RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                System.err.println("Could not restore database transaction mode: " + e.getMessage());
            }
        }
    }

    public synchronized List<ActivityEntry> getRecentAdminActivity(String actorRole) {
        requireAdmin(actorRole);
        requirePostgres();
        List<ActivityEntry> activity = new ArrayList<>();
        String sql = "SELECT action, details, created_at FROM activity_log ORDER BY created_at DESC, id DESC LIMIT 12";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                activity.add(new ActivityEntry(rs.getString("action"), rs.getString("details"),
                        timestampText(rs.getTimestamp("created_at"))));
            }
            return activity;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load recent admin activity.", e);
        }
    }

    public synchronized List<Integer> getAppointmentDays(int year, int month, String actorRole) {
        requireAdmin(actorRole);
        requirePostgres();
        if (month < 1 || month > 12) throw new IllegalArgumentException("Invalid month.");
        List<Integer> days = new ArrayList<>();
        String sql = """
                SELECT DISTINCT EXTRACT(DAY FROM session_date)::INT AS session_day
                FROM appointments
                WHERE session_date >= ? AND session_date < ?
                  AND UPPER(status) <> 'CANCELLED'
                ORDER BY session_day
                """;
        LocalDate monthStart = LocalDate.of(year, month, 1);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(monthStart));
            ps.setDate(2, java.sql.Date.valueOf(monthStart.plusMonths(1)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) days.add(rs.getInt("session_day"));
            }
            return days;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load scheduled appointment days.", e);
        }
    }

    private void insertActivity(int actorId, Integer affectedUserId, String entityType, int entityId,
                                String action, String details) throws SQLException {
        String sql = """
                INSERT INTO activity_log
                    (actor_user_id, affected_user_id, entity_type, entity_id, action, details)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, actorId);
            if (affectedUserId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, affectedUserId);
            ps.setString(3, entityType);
            ps.setInt(4, entityId);
            ps.setString(5, action);
            ps.setString(6, details);
            ps.executeUpdate();
        }
    }

    private void insertNotification(int userId, String message) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO notifications (user_id, message) VALUES (?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, message);
            ps.executeUpdate();
        }
    }

    private void requireAdmin(String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            throw new SecurityException("Only ADMIN accounts can perform this action.");
        }
    }

    private void requirePostgres() {
        if (!connectedToPostgres || connection == null) {
            throw new IllegalStateException("PostgreSQL is required for this admin action.");
        }
    }

    private String timestampText(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toString();
    }

    // Chat / Messenger
    public Conversation getOrCreateConversation(int clientId, String serviceType) {
        if (!"JUSTICE_CARE".equals(serviceType) && !"THOMATT".equals(serviceType)) {
            throw new IllegalArgumentException("Please choose a valid TalkCare service.");
        }
        if (connectedToPostgres && connection != null) {
            String sql = "SELECT * FROM conversations WHERE client_id = ? AND service_type = ? LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, clientId);
                ps.setString(2, serviceType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapConversation(rs);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load the conversation from PostgreSQL.", e);
            }

            int profId = serviceType.equalsIgnoreCase("JUSTICE_CARE") ? 3 : 4;
            String title = serviceType.equalsIgnoreCase("JUSTICE_CARE") ? "JusticeCare Support" : "ThoMatt Consultation";
            String insert = "INSERT INTO conversations (client_id, professional_id, service_type, title) VALUES (?, ?, ?, ?) RETURNING *";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setInt(1, clientId);
                ps.setInt(2, profId);
                ps.setString(3, serviceType);
                ps.setString(4, title);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Conversation conversation = mapConversation(rs);
                        addWelcomeMessage(conversation);
                        return conversation;
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not create the conversation in PostgreSQL.", e);
            }
        }

        for (Conversation c : memoryConversations) {
            if (c.getClientId() == clientId && c.getServiceType().equalsIgnoreCase(serviceType)) {
                return c;
            }
        }

        Conversation newConv = new Conversation();
        newConv.setId(memoryConversations.size() + 1);
        newConv.setClientId(clientId);
        newConv.setProfessionalId(serviceType.equalsIgnoreCase("JUSTICE_CARE") ? 3 : 4);
        newConv.setServiceType(serviceType);
        newConv.setTitle(serviceType.equalsIgnoreCase("JUSTICE_CARE") ? "JusticeCare Support" : "ThoMatt Consultation");
        newConv.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        memoryConversations.add(newConv);
        addWelcomeMessage(newConv);
        return newConv;
    }

    public Conversation getConversation(int conversationId) {
        if (connectedToPostgres && connection != null) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM conversations WHERE id = ?")) {
                ps.setInt(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapConversation(rs) : null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load the conversation from PostgreSQL.", e);
            }
        }
        for (Conversation conversation : memoryConversations) {
            if (conversation.getId() == conversationId) return conversation;
        }
        return null;
    }

    public List<Conversation> getConversationsForProfessional(int professionalId) {
        List<Conversation> conversations = new ArrayList<>();
        if (connectedToPostgres && connection != null) {
            String sql = "SELECT * FROM conversations WHERE professional_id = ? ORDER BY id DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, professionalId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) conversations.add(mapConversation(rs));
                }
                return conversations;
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load assigned conversations from PostgreSQL.", e);
            }
        }
        for (Conversation conversation : memoryConversations) {
            if (conversation.getProfessionalId() != null
                    && conversation.getProfessionalId() == professionalId) {
                conversations.add(conversation);
            }
        }
        conversations.sort(Comparator.comparingInt(Conversation::getId).reversed());
        return conversations;
    }

    private void addWelcomeMessage(Conversation conversation) {
        if (!getMessages(conversation.getId()).isEmpty()) return;

        ChatMessage welcome = new ChatMessage();
        welcome.setConversationId(conversation.getId());
        welcome.setSenderId(conversation.getProfessionalId());
        welcome.setSenderName("JUSTICE_CARE".equals(conversation.getServiceType())
                ? "Atty. Sofia Ramos" : "Dr. Eleanor Reyes");
        welcome.setSenderRole("PROFESSIONAL");
        welcome.setMessageText("JUSTICE_CARE".equals(conversation.getServiceType())
                ? "Hello, I am Atty. Sofia Ramos from JusticeCare. How can we support and protect your rights today?"
                : "Welcome to ThoMatt. I am Dr. Eleanor Reyes (R.Psy / L.P.T.). This is a safe and confidential environment. Tell me your thoughts...");
        welcome.setAutoReply(false);
        welcome.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        saveMessage(welcome);
    }

    public List<ChatMessage> getMessages(int conversationId) {
        List<ChatMessage> list = new ArrayList<>();
        if (connectedToPostgres && connection != null) {
            String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY id ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, conversationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapMessage(rs));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not load messages from PostgreSQL.", e);
            }
        }

        for (ChatMessage m : memoryMessages) {
            if (m.getConversationId() == conversationId) {
                list.add(m);
            }
        }
        return list;
    }

    /**
     * Requirement:
     * "dun sa chatbot, kapag nag message ang user or client, dapat may auto reply na
     * 'thank you for your message, please wait for the reply of the assigned Professional',
     * then dapat pag nakita na ng professional ang message ay makakapagreply sila"
     */
    public List<ChatMessage> sendClientMessage(int conversationId, int clientId, String senderName, String messageText, String attachmentUrl) {
        List<ChatMessage> added = new ArrayList<>();

        // 1. Client's message
        ChatMessage userMsg = new ChatMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setSenderId(clientId);
        userMsg.setSenderName(senderName);
        userMsg.setSenderRole("CLIENT");
        userMsg.setMessageText(messageText);
        userMsg.setAttachmentUrl(attachmentUrl);
        userMsg.setAutoReply(false);
        userMsg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        saveMessage(userMsg);
        added.add(userMsg);

        // 2. Auto reply from the system / professional bot
        ChatMessage autoMsg = new ChatMessage();
        autoMsg.setConversationId(conversationId);
        autoMsg.setSenderId(null);
        autoMsg.setSenderName("TalkCare Auto-Assistant");
        autoMsg.setSenderRole("SYSTEM");
        autoMsg.setMessageText("thank you for your message, please wait for the reply of the assigned Professional");
        autoMsg.setAutoReply(true);
        autoMsg.setCreatedAt(new Timestamp(System.currentTimeMillis() + 500));
        saveMessage(autoMsg);
        added.add(autoMsg);

        return added;
    }

    public ChatMessage sendProfessionalReply(int conversationId, int profId, String profName, String messageText) {
        ChatMessage profMsg = new ChatMessage();
        profMsg.setConversationId(conversationId);
        profMsg.setSenderId(profId);
        profMsg.setSenderName(profName != null ? profName : "Assigned Professional");
        profMsg.setSenderRole("PROFESSIONAL");
        profMsg.setMessageText(messageText);
        profMsg.setAutoReply(false);
        profMsg.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        saveMessage(profMsg);
        return profMsg;
    }

    private void saveMessage(ChatMessage msg) {
        if (connectedToPostgres && connection != null) {
            String sql = "INSERT INTO messages (conversation_id, sender_id, sender_name, sender_role, message_text, is_auto_reply, attachment_url) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, msg.getConversationId());
                if (msg.getSenderId() != null) ps.setInt(2, msg.getSenderId()); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, msg.getSenderName());
                ps.setString(4, msg.getSenderRole());
                ps.setString(5, msg.getMessageText());
                ps.setBoolean(6, msg.isAutoReply());
                ps.setString(7, msg.getAttachmentUrl());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        msg.setId(rs.getInt(1));
                        msg.setCreatedAt(rs.getTimestamp(2));
                        return;
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Could not save the message to PostgreSQL.", e);
            }
        }
        msg.setId(memoryMessages.size() + 1);
        memoryMessages.add(msg);
    }

    // Admin & Dashboard metrics
    public synchronized Map<String, Object> getAdminMetrics(String actorRole) {
        requireAdmin(actorRole);
        requirePostgres();
        Map<String, Object> stats = new HashMap<>();
        try (PreparedStatement totalUsersQuery = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet users = totalUsersQuery.executeQuery()) {
            users.next();
            stats.put("totalUsers", users.getInt(1));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load the total user count.", e);
        }
        try (PreparedStatement pendingQuery = connection.prepareStatement("""
                     SELECT
                       (SELECT COUNT(*) FROM users WHERE status = ?) +
                       (SELECT COUNT(*) FROM reports WHERE UPPER(REPLACE(status, ' ', '_')) = ?)
                     """)) {
            pendingQuery.setString(1, "PENDING_VERIFICATION");
            pendingQuery.setString(2, "UNDER_REVIEW");
            try (ResultSet pending = pendingQuery.executeQuery()) {
                pending.next();
                stats.put("pendingApprovals", pending.getInt(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load pending approval counts.", e);
        }
        try (PreparedStatement sessionsQuery = connection.prepareStatement(
                "SELECT COUNT(*) FROM appointments WHERE session_date = CURRENT_DATE AND UPPER(status) <> 'CANCELLED'");
             ResultSet sessions = sessionsQuery.executeQuery()) {
            sessions.next();
            stats.put("sessionToday", sessions.getInt(1));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load today's session count.", e);
        }
        return stats;
    }

    public synchronized List<User> getActiveUserProfiles(String actorRole) {
        if (!"ADMIN".equals(actorRole)) {
            throw new SecurityException("Only ADMIN accounts can read user profiles.");
        }
        if (connectedToPostgres && connection != null) {
            List<User> list = new ArrayList<>();
            String sql = "SELECT * FROM users ORDER BY id ASC LIMIT 10";
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(mapUser(rs));
                }
                return list;
            } catch (SQLException ignored) {}
        }
        return new ArrayList<>(memoryUsers);
    }

    // Helpers
    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        u.setContactNo(rs.getString("contact_no"));
        u.setAddress(rs.getString("address"));
        u.setRole(normalizeRole(rs.getString("role")));
        u.setProfilePictureUrl(rs.getString("profile_picture_url"));
        u.setIdType(rs.getString("id_type"));
        u.setIdNumber(rs.getString("id_number"));
        u.setIdDocumentUrl(rs.getString("id_document_url"));
        u.setStatus(rs.getString("status"));
        return u;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank() || role.equalsIgnoreCase("CLIENT") || role.equalsIgnoreCase("PEER")) return "USER";
        if (role.equalsIgnoreCase("PROFESSIONAL")) return "THERAPIST";
        return role.toUpperCase(Locale.ROOT);
    }

    private Report mapReport(ResultSet rs) throws SQLException {
        Report r = new Report();
        r.setId(rs.getInt("id"));
        r.setReportCode(rs.getString("report_code"));
        int uid = rs.getInt("user_id");
        if (!rs.wasNull()) r.setUserId(uid);
        r.setFullName(rs.getString("full_name"));
        r.setEmail(rs.getString("email"));
        r.setAddress(rs.getString("address"));
        r.setContactNo(rs.getString("contact_no"));
        r.setConcernType(rs.getString("concern_type"));
        r.setInvolvedWitness(rs.getString("involved_witness"));
        r.setConcernDetails(rs.getString("concern_details"));
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }

    private Conversation mapConversation(ResultSet rs) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getInt("id"));
        c.setClientId(rs.getInt("client_id"));
        int profId = rs.getInt("professional_id");
        if (!rs.wasNull()) c.setProfessionalId(profId);
        c.setServiceType(rs.getString("service_type"));
        c.setTitle(rs.getString("title"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getInt("id"));
        m.setConversationId(rs.getInt("conversation_id"));
        int sId = rs.getInt("sender_id");
        if (!rs.wasNull()) m.setSenderId(sId);
        m.setSenderName(rs.getString("sender_name"));
        m.setSenderRole(rs.getString("sender_role"));
        m.setMessageText(rs.getString("message_text"));
        m.setAutoReply(rs.getBoolean("is_auto_reply"));
        m.setAttachmentUrl(rs.getString("attachment_url"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        return m;
    }
}
