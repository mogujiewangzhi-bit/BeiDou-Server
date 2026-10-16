CREATE TABLE superchar_audit (
    id CHAR(36) PRIMARY KEY,
    characterid INT NOT NULL,
    accountid INT NOT NULL,
    parameters JSON NOT NULL,
    before_state JSON NOT NULL,
    after_state JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_at TIMESTAMP NULL,
    INDEX superchar_character_idx (characterid, created_at)
) ENGINE=InnoDB;

CREATE TABLE superchar_restore_audit (
    id CHAR(36) PRIMARY KEY,
    backupid CHAR(36) NOT NULL UNIQUE,
    characterid INT NOT NULL,
    accountid INT NOT NULL,
    before_state JSON NOT NULL,
    restored_state JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'superchar', 6, 1, 'SuperCharacterCommand', 6
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax='superchar');
