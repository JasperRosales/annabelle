-- TalkCare PostgreSQL Schema

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    contact_no VARCHAR(50),
    address TEXT,
    role VARCHAR(20) DEFAULT 'USER', -- USER, THERAPIST, CASE_HANDLER, ADMIN
    profile_picture_url TEXT,
    id_type VARCHAR(100),
    id_number VARCHAR(100),
    id_document_url TEXT,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, PENDING_VERIFICATION, VERIFIED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS reports (
    id SERIAL PRIMARY KEY,
    report_code VARCHAR(50) UNIQUE,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    address TEXT,
    contact_no VARCHAR(50),
    concern_type VARCHAR(100) NOT NULL,
    involved_witness VARCHAR(150),
    concern_details TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'Under Review', -- 'Under Review', 'In Progress', 'Resolved'
    remarks TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
);

CREATE TABLE IF NOT EXISTS activity_log (
    id SERIAL PRIMARY KEY,
    actor_user_id INT REFERENCES users(id) ON DELETE SET NULL,
    affected_user_id INT REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id INT NOT NULL,
    action VARCHAR(80) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversations (
    id SERIAL PRIMARY KEY,
    client_id INT REFERENCES users(id) ON DELETE CASCADE,
    professional_id INT REFERENCES users(id) ON DELETE SET NULL,
    service_type VARCHAR(50) NOT NULL, -- 'JUSTICE_CARE', 'THOMATT'
    title VARCHAR(150),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    conversation_id INT REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id INT REFERENCES users(id) ON DELETE SET NULL,
    sender_name VARCHAR(150) NOT NULL,
    sender_role VARCHAR(50) NOT NULL, -- 'CLIENT', 'PROFESSIONAL', 'SYSTEM'
    message_text TEXT NOT NULL,
    is_auto_reply BOOLEAN DEFAULT FALSE,
    attachment_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS appointments (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    professional_id INT REFERENCES users(id) ON DELETE SET NULL,
    service_name VARCHAR(100) NOT NULL,
    session_date DATE NOT NULL,
    session_time VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'SCHEDULED', -- SCHEDULED, COMPLETED, CANCELLED
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Users (matching PDF wireframe profiles)
INSERT INTO users (username, full_name, email, password, contact_no, address, role, status)
VALUES 
    ('admin', 'Admin User', 'admin@talkcare.com', 'admin123', '09123456789', 'TalkCare HQ, Manila', 'ADMIN', 'VERIFIED'),
    ('maxsmith', 'Max Smith', 'max.smith@talkcare.com', 'max123', '096364664286', 'Quezon City, Philippines', 'USER', 'VERIFIED'),
    ('sofiaramos', 'Atty. Sofia Ramos', 'sofia.ramos@talkcare.com', 'justice123', '09171234567', 'JusticeCare Center, Manila', 'CASE_HANDLER', 'VERIFIED'),
    ('eleanorreyes', 'Dr. Eleanor Reyes', 'eleanor.reyes@talkcare.com', 'therapist123', '09189876543', 'ThoMatt Mental Health Clinic', 'THERAPIST', 'VERIFIED')
ON CONFLICT (username) DO NOTHING;

-- Seed Sample Reports (matching PDF Page 8 & 9 & 10)
INSERT INTO reports (report_code, full_name, email, address, contact_no, concern_type, involved_witness, concern_details, status, created_at)
VALUES
    ('Report #1', 'Max Smith', 'max.smith@talkcare.com', 'Quezon City', '096364664286', 'Domestic Injustice', 'Family counselor', 'Client experienced unfair family dispute and sought immediate legal advice and counseling.', 'Resolved', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('Report #2', 'Alex Taylor', 'alex.t@example.com', 'Batangas City', '09281234567', 'Workplace Discrimination', 'Coworker Maria', 'Unfair treatment and severe workplace pressure causing acute anxiety and distress.', 'In Progress', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('Report #3', 'Jordan Lee', 'jordan.lee@example.com', 'Manila', '09391234567', 'Student Mental Distress', 'School Guidance Counselor', 'Academic overload and severe anxiety needing confidential psychological consultation.', 'Under Review', CURRENT_TIMESTAMP - INTERVAL '4 hours')
ON CONFLICT (report_code) DO NOTHING;

-- Seed Conversations and Default Auto-Replies
INSERT INTO conversations (id, client_id, professional_id, service_type, title)
VALUES 
    (1, 2, 3, 'JUSTICE_CARE', 'Legal Rights & Victim Support Case #101'),
    (2, 2, 4, 'THOMATT', 'Mental Health & Well-being Consultation')
ON CONFLICT (id) DO NOTHING;

INSERT INTO messages (conversation_id, sender_id, sender_name, sender_role, message_text, is_auto_reply, created_at)
VALUES
    (1, 3, 'Atty. Sofia Ramos', 'PROFESSIONAL', 'Hello, I am Atty. Sofia Ramos from JusticeCare. How can we support and protect your rights today?', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    (2, 4, 'Dr. Eleanor Reyes', 'PROFESSIONAL', 'Welcome to ThoMatt. I am Dr. Eleanor Reyes (R.Psy / L.P.T.). This is a completely safe, judgment-free space. Tell me your thoughts...', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours')
ON CONFLICT DO NOTHING;
