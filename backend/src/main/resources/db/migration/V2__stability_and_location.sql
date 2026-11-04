ALTER TABLE messages ADD COLUMN sender_id BIGINT NULL;
ALTER TABLE messages ADD COLUMN receiver_id BIGINT NULL;
UPDATE messages SET sender_id = user_id, receiver_id = user_id;
ALTER TABLE messages ALTER COLUMN receiver_id SET NOT NULL;
CREATE INDEX idx_message_receiver ON messages(receiver_id, created_at);

ALTER TABLE school_applications ADD COLUMN decided_by BIGINT NULL;
ALTER TABLE school_applications ADD COLUMN decided_at TIMESTAMP NULL;

ALTER TABLE takeout_orders ADD COLUMN demo TINYINT NOT NULL DEFAULT 1;
ALTER TABLE takeout_orders ADD CONSTRAINT uk_order_user_takeout UNIQUE (user_id, takeout_id);

CREATE TABLE user_locations (
  id VARCHAR(40) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  school_id VARCHAR(80) NOT NULL,
  lat DOUBLE NOT NULL,
  lng DOUBLE NOT NULL,
  accuracy INT NOT NULL DEFAULT 0,
  source VARCHAR(20) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_location_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX idx_location_user ON user_locations(user_id, updated_at);

CREATE INDEX idx_action_user_type ON user_actions(user_id, action_type, created_at);
CREATE INDEX idx_content_type_campus ON content_items(type, campus);
