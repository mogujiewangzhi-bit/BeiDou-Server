CREATE TABLE admin_skill_edits (
    id CHAR(36) PRIMARY KEY,
    character_id INT NOT NULL,
    account_id INT NOT NULL,
    actor VARCHAR(64) NOT NULL,
    before_state JSON NOT NULL,
    after_state JSON NOT NULL,
    changes JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX skill_edits_history (character_id, created_at)
) ENGINE=InnoDB;
