CREATE TABLE app_users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(80) NOT NULL,
  role VARCHAR(20) NOT NULL,
  school_id VARCHAR(80) NOT NULL,
  profile_json TEXT NOT NULL,
  edited INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(30) NOT NULL,
  content_id VARCHAR(100) NOT NULL,
  owner_id BIGINT NULL,
  campus VARCHAR(80) NULL,
  school_id VARCHAR(80) NULL,
  payload_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_content_type_id UNIQUE (type, content_id),
  CONSTRAINT fk_content_owner FOREIGN KEY (owner_id) REFERENCES app_users(id)
);
CREATE INDEX idx_content_type_owner ON content_items(type, owner_id);
CREATE INDEX idx_content_school ON content_items(type, school_id);

CREATE TABLE user_actions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id VARCHAR(100) NOT NULL,
  value_json TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_action UNIQUE (user_id, action_type, target_type, target_id),
  CONSTRAINT fk_action_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_action_target ON user_actions(action_type, target_type, target_id);

CREATE TABLE comments (
  id VARCHAR(40) PRIMARY KEY,
  post_id VARCHAR(100) NOT NULL,
  user_id BIGINT NOT NULL,
  author VARCHAR(80) NOT NULL,
  text VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_comment_post ON comments(post_id, created_at);

CREATE TABLE messages (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_message_user ON messages(user_id, created_at);

CREATE TABLE histories (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  title VARCHAR(500) NOT NULL,
  icon VARCHAR(30) NULL,
  score VARCHAR(50) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_history_user ON histories(user_id, created_at);

CREATE TABLE school_applications (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  university VARCHAR(100) NOT NULL,
  campus VARCHAR(100) NOT NULL,
  city VARCHAR(80) NOT NULL,
  contact VARCHAR(80) NOT NULL,
  phone VARCHAR(40) NOT NULL,
  email VARCHAR(120) NULL,
  canteens VARCHAR(40) NULL,
  note VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL,
  CONSTRAINT fk_application_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_application_user ON school_applications(user_id, created_at);

CREATE TABLE takeout_orders (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  takeout_id VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_order_user ON takeout_orders(user_id, created_at);
