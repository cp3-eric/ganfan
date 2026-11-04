-- V2（MySQL 版）
-- 修复说明：历史版本第 4 行使用 'ALTER TABLE ... ALTER COLUMN ... SET NOT NULL'，
-- 这是 H2/PostgreSQL 语法，MySQL 8.4 报 1064 语法错误（Error Code 1064）。
-- 且该脚本失败前第 1~3 行已在目标库执行（sender_id/receiver_id 列已存在），
-- 因此本版本对这两列做存在性判断，兼容“半成品库”与“全新库”两种状态；
-- MySQL 设置非空需使用 MODIFY COLUMN。

DROP PROCEDURE IF EXISTS ganfan_v2_ensure_message_cols;

CREATE PROCEDURE ganfan_v2_ensure_message_cols()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'messages'
                   AND COLUMN_NAME = 'sender_id') THEN
    ALTER TABLE messages ADD COLUMN sender_id BIGINT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'messages'
                   AND COLUMN_NAME = 'receiver_id') THEN
    ALTER TABLE messages ADD COLUMN receiver_id BIGINT NULL;
  END IF;
END;

CALL ganfan_v2_ensure_message_cols();
DROP PROCEDURE IF EXISTS ganfan_v2_ensure_message_cols;

UPDATE messages SET sender_id = user_id, receiver_id = user_id;
ALTER TABLE messages MODIFY COLUMN receiver_id BIGINT NOT NULL;
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
