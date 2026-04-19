CREATE DATABASE IF NOT EXISTS messagerie_db;
USE messagerie_db;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS emails (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    content TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELIMITER //

DROP PROCEDURE IF EXISTS authenticate_user //
CREATE PROCEDURE authenticate_user(IN p_username VARCHAR(100), IN p_password VARCHAR(255), OUT p_is_valid BOOLEAN)
BEGIN
    SELECT COUNT(*) INTO @cnt FROM users WHERE username = p_username AND password_hash = p_password;
    IF @cnt > 0 THEN
        SET p_is_valid = TRUE;
    ELSE
        SET p_is_valid = FALSE;
    END IF;
END //

DROP PROCEDURE IF EXISTS store_email //
CREATE PROCEDURE store_email(IN p_sender VARCHAR(255), IN p_recipient VARCHAR(255), IN p_subject VARCHAR(255), IN p_content TEXT)
BEGIN
    INSERT INTO emails (sender, recipient, subject, content) VALUES (p_sender, p_recipient, p_subject, p_content);
END //

DROP PROCEDURE IF EXISTS fetch_emails //
CREATE PROCEDURE fetch_emails(IN p_user VARCHAR(100))
BEGIN
    SELECT * FROM emails WHERE recipient = p_user OR recipient LIKE CONCAT(p_user, '@%') ORDER BY created_at ASC;
END //

DROP PROCEDURE IF EXISTS delete_email //
CREATE PROCEDURE delete_email(IN p_email_id INT)
BEGIN
    DELETE FROM emails WHERE id = p_email_id;
END //

DROP PROCEDURE IF EXISTS update_password //
CREATE PROCEDURE update_password(IN p_user VARCHAR(100), IN p_new_password VARCHAR(255))
BEGIN
    UPDATE users SET password_hash = p_new_password WHERE username = p_user;
END //

DELIMITER ;
