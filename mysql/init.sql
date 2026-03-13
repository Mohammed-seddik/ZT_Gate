CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) UNIQUE NOT NULL,
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  password_hash CHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (username, email, display_name, password_hash, role)
VALUES
  ('alice', 'alice@example.local', 'Alice', SHA2('password123', 256), 'admin'),
  ('bob', 'bob@example.local', 'Bob', SHA2('password123', 256), 'user')
ON DUPLICATE KEY UPDATE username = VALUES(username);
