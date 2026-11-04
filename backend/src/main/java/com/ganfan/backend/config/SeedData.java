package com.ganfan.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeedData implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public SeedData(JdbcTemplate jdbc, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try (InputStream in = new ClassPathResource("seed.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            String defaultSchoolId = root.path("school").path("id").asText("zjut-pingfeng");
            String defaultProfile = objectMapper.writeValueAsString(root.path("profile"));

            insertMeta(root, "school");
            insertMeta(root, "profile");
            insertMeta(root, "topics");
            insertMeta(root, "kings");
            insertMeta(root, "ranks");
            insertMeta(root, "mapBlocks");
            insertMeta(root, "challenge");

            insertContentArray(root, "SCHOOL", defaultSchoolId);
            insertContentArray(root, "CANTEEN", defaultSchoolId);
            insertContentArray(root, "TAKEOUT", defaultSchoolId);
            insertContentArray(root, "STALL", defaultSchoolId);
            insertContentArray(root, "MEETUP", defaultSchoolId);
            insertContentArray(root, "POST", defaultSchoolId);

            if (countUsers() == 0) {
                insertUser("demo@ganfan.local", "demo123456", "干饭小分队", "USER", defaultSchoolId, defaultProfile);
                insertUser("admin@ganfan.local", "admin123456", "平台管理员", "ADMIN", defaultSchoolId, defaultProfile);
            }
        }
    }

    private void insertMeta(JsonNode root, String key) {
        if (!root.has(key)) return;
        String exists = selectPayload("META", key);
        if (exists == null) {
            jdbc.update("INSERT INTO content_items(type, content_id, owner_id, campus, school_id, payload_json) VALUES(?,?,?,?,?,?)",
                    "META", key, null, null, null, root.path(key).toString());
        }
    }

    private void insertContentArray(JsonNode root, String type, String defaultSchoolId) {
        String key = switch (type) {
            case "SCHOOL" -> "schools";
            case "CANTEEN" -> "canteens";
            case "TAKEOUT" -> "takeouts";
            case "STALL" -> "stalls";
            case "MEETUP" -> "meetups";
            case "POST" -> "posts";
            default -> "";
        };
        JsonNode array = root.path(key);
        if (!array.isArray()) return;
        for (JsonNode node : array) {
            String contentId = node.path("id").asText();
            if (contentId.isBlank() || selectPayload(type, contentId) != null) continue;
            String campus = node.path("campus").asText(null);
            String schoolId = node.path("schoolId").asText(defaultSchoolId);
            if (type.equals("SCHOOL")) {
                schoolId = node.path("id").asText(defaultSchoolId);
                campus = node.path("campus").asText(null);
            }
            jdbc.update("INSERT INTO content_items(type, content_id, owner_id, campus, school_id, payload_json) VALUES(?,?,?,?,?,?)",
                    type, contentId, null, campus, schoolId, node.toString());
        }
    }

    private String selectPayload(String type, String contentId) {
        List<String> rows = jdbc.query("SELECT payload_json FROM content_items WHERE type=? AND content_id=?",
                (rs, i) -> rs.getString(1), type, contentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countUsers() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        return count == null ? 0 : count;
    }

    private void insertUser(String email, String rawPassword, String name, String role, String schoolId, String profileJson) {
        jdbc.update("INSERT INTO app_users(email, password_hash, name, role, school_id, profile_json, edited) VALUES(?,?,?,?,?,?,?)",
                email, passwordEncoder.encode(rawPassword), name, role, schoolId, profileJson, 0);
    }
}
