CREATE TABLE admin_resource_grants (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    character_id INT NOT NULL,
    account_id INT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP NULL,
    attempted_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_admin_grant_request (request_id),
    KEY idx_admin_grant_pending (character_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
