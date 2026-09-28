CREATE TABLE IF NOT EXISTS health_model (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    encrypted_key TEXT NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS health_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    member VARCHAR(100) NOT NULL,
    role VARCHAR(12) NOT NULL,
    content TEXT NOT NULL,
    sources JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_health_chat_member_conversation (member, conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
