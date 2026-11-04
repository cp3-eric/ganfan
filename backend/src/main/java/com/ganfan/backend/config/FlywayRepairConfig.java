package com.ganfan.backend.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 启动策略：先 repair 再 migrate。
 *
 * 背景：V2 迁移曾因 MySQL 方言不兼容在执行中途失败，数据库的
 * flyway_schema_history 表中会残留一条 success=0 的失败记录。
 * Flyway 默认检测到失败记录会拒绝继续迁移并要求 repair，
 * 因此在每次启动时自动执行 repair（清除失败记录、对齐校验和），
 * 无需人工连接数据库清理。
 */
@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
