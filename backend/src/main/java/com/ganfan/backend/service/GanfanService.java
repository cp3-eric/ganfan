package com.ganfan.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganfan.backend.common.ApiException;
import com.ganfan.backend.dto.ApiDtos;
import com.ganfan.backend.dto.ApiDtos.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GanfanService {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.ganfan.backend.security.JwtService jwtService;

    public GanfanService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                         PasswordEncoder passwordEncoder,
                         com.ganfan.backend.security.JwtService jwtService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record UserRow(Long id, String email, String passwordHash, String name, String role, String schoolId, String profileJson, int edited) {}
    public record ContentRow(String type, String contentId, Long ownerId, String campus, String schoolId, String payloadJson) {}

    /* ---------------- 账号与认证 ---------------- */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (existsEmail(email)) throw ApiException.conflict("邮箱已注册");
        String schoolId = defaultSchoolId();
        String profileJson = defaultProfileJson();
        Map<String, Object> profile = readMap(profileJson);
        profile.put("name", request.name().trim());
        profile.put("campus", campusOfSchool(schoolId));
        jdbc.update("INSERT INTO app_users(email, password_hash, name, role, school_id, profile_json, edited) VALUES(?,?,?,?,?,?,?)",
                email, passwordEncoder.encode(request.password()), request.name().trim(), "USER", schoolId, writeJson(profile), 0);
        UserRow user = findUserByEmail(email);
        return authResult(user);
    }

    public AuthResult login(LoginRequest request) {
        UserRow user = findUserByEmail(request.email().trim().toLowerCase());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw ApiException.unauthorized("邮箱或密码错误");
        }
        return authResult(user);
    }

    private AuthResult authResult(UserRow user) {
        String token = jwtService.issue(user.id(), user.email(), user.role());
        return new AuthResult(token, new AuthUser(user.id(), user.email(), user.name(), user.role(), user.schoolId()));
    }

    public Map<String, Object> me(UserRow user) {
        return Map.of(
                "id", user.id(),
                "email", user.email(),
                "name", user.name(),
                "role", user.role(),
                "schoolId", user.schoolId()
        );
    }

    /* ---------------- 首屏数据 ---------------- */
    public Map<String, Object> bootstrap(UserRow user) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        putMeta(catalog, "school");
        putMeta(catalog, "profile");
        putMeta(catalog, "topics");
        putMeta(catalog, "kings");
        putMeta(catalog, "ranks");
        putMeta(catalog, "mapBlocks");
        putMeta(catalog, "challenge");
        catalog.put("schools", contentArray("SCHOOL"));
        catalog.put("canteens", contentArray("CANTEEN"));
        catalog.put("takeouts", contentArray("TAKEOUT"));
        catalog.put("stalls", contentArray("STALL"));
        catalog.put("meetups", contentArray("MEETUP", user.id(), false));
        catalog.put("posts", contentArray("POST", user.id(), false));
        return Map.of("catalog", catalog, "state", state(user), "user", me(user));
    }

    /* ---------------- 可变操作 ---------------- */
    @Transactional
    public Map<String, Object> updateProfile(UserRow user, ProfileRequest request) {
        if (request.budgetMin() > request.budgetMax()) {
            throw new ApiException(400, 40002, "参数校验失败", Map.of("budgetMin", "预算下限不能大于上限", "budgetMax", "预算上限不能小于下限"));
        }
        Map<String, Object> profile = readMap(user.profileJson());
        profile.put("name", request.name().trim());
        profile.put("spicy", request.spicy());
        profile.put("budget", List.of(request.budgetMin(), request.budgetMax()));
        profile.put("sleep", request.sleep());
        profile.put("social", request.social());
        profile.put("dislikes", request.dislikes() == null ? List.of() : request.dislikes());
        profile.put("updatedAt", timeLabel());
        jdbc.update("UPDATE app_users SET name=?, profile_json=?, edited=edited+1 WHERE id=?",
                request.name().trim(), writeJson(profile), user.id());
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> selectSchool(UserRow user, PreferenceRequest request) {
        Map<String, Object> school = findSchool(request.schoolId());
        if (school == null) throw ApiException.notFound("学校/校区不存在");
        Map<String, Object> profile = readMap(user.profileJson());
        profile.put("campus", school.getOrDefault("campus", profile.get("campus")));
        jdbc.update("UPDATE app_users SET school_id=?, profile_json=? WHERE id=?",
                request.schoolId(), writeJson(profile), user.id());
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> favoriteStall(UserRow user, String stallId, boolean enable) {
        requireContentForUser(user, "STALL", stallId);
        if (enable) {
            boolean inserted = insertAction(user.id(), "FAVORITE_STALL", "STALL", stallId, null);
            if (inserted) insertHistory(user.id(), "收藏", "收藏 · " + stallId, "🔖", "");
        } else {
            deleteAction(user.id(), "FAVORITE_STALL", "STALL", stallId);
        }
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> collectPost(UserRow user, String postId, boolean enable) {
        requireContentForUser(user, "POST", postId);
        if (enable) insertAction(user.id(), "COLLECT_POST", "POST", postId, null);
        else deleteAction(user.id(), "COLLECT_POST", "POST", postId);
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> likePost(UserRow user, String postId, boolean enable) {
        requireContentForUser(user, "POST", postId);
        if (enable) insertAction(user.id(), "LIKE_POST", "POST", postId, null);
        else deleteAction(user.id(), "LIKE_POST", "POST", postId);
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> joinMeetup(UserRow user, String meetupId, boolean enable) {
        ContentRow meetup = requireContentForUser(user, "MEETUP", meetupId);
        if (enable && user.id().equals(meetup.ownerId())) {
            throw ApiException.conflict("发起人已占用座位，无需重复加入");
        }
        String lockedJson = jdbc.queryForObject(
                "SELECT payload_json FROM content_items WHERE type='MEETUP' AND content_id=? FOR UPDATE",
                String.class, meetupId);
        Map<String, Object> payload = readMap(lockedJson);
        requireSameCampus(user, String.valueOf(payload.getOrDefault("campus", "")));
        if (enable) {
            if (hasAction(user.id(), "JOIN_MEETUP", "MEETUP", meetupId)) return state(loadUser(user.id()));
            int occupied = baseSeat(payload) + memberCount(meetupId, user.id());
            int size = intOf(payload.get("size"), 1);
            if (occupied >= size) throw ApiException.conflict("饭局已满员");
            if (!insertAction(user.id(), "JOIN_MEETUP", "MEETUP", meetupId, null)) return state(loadUser(user.id()));
            insertHistory(user.id(), "约饭", payload.getOrDefault("owner", "同学") + " · " + payload.getOrDefault("dish", ""), String.valueOf(payload.getOrDefault("avatar", "🤝")), (occupied + 1) + "/" + size);
        } else {
            deleteAction(user.id(), "JOIN_MEETUP", "MEETUP", meetupId);
        }
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> greet(UserRow user, String meetupId, GreetRequest request) {
        ContentRow meetup = requireContentForUser(user, "MEETUP", meetupId);
        Map<String, Object> payload = readMap(meetup.payloadJson());
        requireSameCampus(user, String.valueOf(payload.getOrDefault("campus", "")));
        if (hasAction(user.id(), "GREET_MEETUP", "MEETUP", meetupId)) throw ApiException.conflict("已经打过招呼");
        if (!insertAction(user.id(), "GREET_MEETUP", "MEETUP", meetupId, Map.of("message", request.message().trim()))) {
            return state(loadUser(user.id()));
        }
        Long receiverId = meetup.ownerId() == null ? user.id() : meetup.ownerId();
        String ownerName = String.valueOf(payload.getOrDefault("owner", "同学"));
        String title = receiverId.equals(user.id())
                ? "已向 " + ownerName + " 发送：" + request.message().trim()
                : "来自 " + userName(user) + "：" + request.message().trim();
        insertMessage(receiverId, user.id(), title);
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> createMeetup(UserRow user, MeetupRequest request) {
        String schoolId = user.schoolId();
        String campus = campusOfSchool(schoolId);
        requireCanteenForUser(user, request.canteen());
        Map<String, Object> payload = new LinkedHashMap<>();
        String id = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        payload.put("id", id);
        payload.put("owner", userName(user));
        payload.put("avatar", "🍚");
        payload.put("college", "计算机学院");
        payload.put("grade", "大三");
        payload.put("when", request.when() + " " + request.time());
        payload.put("today", "今晚".equals(request.when()));
        payload.put("canteen", request.canteen());
        payload.put("floor", "1F");
        payload.put("dish", request.dish().trim());
        payload.put("want", request.want());
        payload.put("seat", 1);
        payload.put("size", request.want() + 1);
        payload.put("budget", request.budget());
        payload.put("base", 90);
        payload.put("campus", campus);
        payload.put("schoolId", schoolId);
        payload.put("distance", "同校区 · 步行 5 分钟");
        payload.put("tags", request.tags() == null || request.tags().isEmpty() ? List.of("同校区") : request.tags());
        payload.put("mode", "拼桌");
        payload.put("featured", false);
        payload.put("postedAt", "刚刚发布");
        payload.put("created", true);
        payload.put("note", request.note() == null ? "" : request.note());
        insertContent("MEETUP", id, user.id(), campus, schoolId, payload);
        insertHistory(user.id(), "约饭", "发起 " + request.dish() + " · " + request.canteen(), "🤝", "");
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> createPost(UserRow user, PostRequest request) {
        String schoolId = user.schoolId();
        String campus = campusOfSchool(schoolId);
        String id = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<String> tags = request.tags() == null || request.tags().isEmpty() ? List.of("隐藏吃法") : request.tags();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("author", userName(user));
        payload.put("avatar", "🍚");
        payload.put("emoji", "🍽");
        payload.put("time", "刚刚");
        payload.put("canteen", request.canteen());
        payload.put("campus", campus);
        payload.put("schoolId", schoolId);
        payload.put("meta", request.place() + " · 人均 " + request.price());
        payload.put("text", request.text().trim());
        payload.put("tags", buildPostTags(tags, request.price()));
        payload.put("likes", 0);
        payload.put("comments", 0);
        payload.put("favs", 0);
        payload.put("verified", true);
        payload.put("topic", tags.get(0));
        payload.put("code", "C-" + timeLabel().replace("-", "").replace(" ", "").replace(":", "") + "-" + id.substring(id.length() - 3));
        payload.put("clock", timeLabel().split(" ").length > 1 ? timeLabel().split(" ")[1] : timeLabel());
        insertContent("POST", id, user.id(), campus, schoolId, payload);
        insertHistory(user.id(), "打卡", request.text().trim().substring(0, Math.min(16, request.text().trim().length())) + "…", "📸", "");
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> comment(UserRow user, String postId, CommentRequest request) {
        requireContentForUser(user, "POST", postId);
        jdbc.update("INSERT INTO comments(id, post_id, user_id, author, text, created_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), postId, user.id(), userName(user), request.text().trim(), Instant.now());
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> applySchool(UserRow user, ApplicationRequest request) {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_applications WHERE user_id=? AND university=? AND campus=? AND status='待审核'",
                Integer.class, user.id(), request.university().trim(), request.campus().trim());
        if (pending != null && pending > 0) throw ApiException.conflict("该学校的入驻申请正在审核中");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO school_applications(id, user_id, university, campus, city, contact, phone, email, canteens, note, status, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, user.id(), request.university().trim(), request.campus().trim(), request.city().trim(),
                request.contact().trim(), request.phone().trim(), request.email(), request.canteens(), request.note(),
                "待审核", Instant.now(), Instant.now());
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> orderTakeout(UserRow user, String takeoutId) {
        Map<String, Object> takeout = requirePayloadForUser(user, "TAKEOUT", takeoutId);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM takeout_orders WHERE user_id=? AND takeout_id=?",
                Integer.class, user.id(), takeoutId);
        if (existing != null && existing > 0) return state(loadUser(user.id()));
        try {
            jdbc.update("INSERT INTO takeout_orders(id, user_id, takeout_id, created_at) VALUES(?,?,?,?)",
                    UUID.randomUUID().toString(), user.id(), takeoutId, Instant.now());
            insertHistory(user.id(), "外卖", String.valueOf(takeout.getOrDefault("name", takeoutId)), String.valueOf(takeout.getOrDefault("emoji", "🍽")), "");
        } catch (DuplicateKeyException ignored) {
            // 并发重复点单按幂等处理
        }
        return state(loadUser(user.id()));
    }

    @Transactional
    public Map<String, Object> resetDemo(UserRow user) {
        List<String> contentIds = jdbc.queryForList("SELECT content_id FROM content_items WHERE owner_id=?", String.class, user.id());
        if (!contentIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(contentIds.size(), "?"));
            jdbc.update("DELETE FROM user_actions WHERE target_type IN ('POST','MEETUP') AND target_id IN (" + placeholders + ")",
                    contentIds.toArray());
            jdbc.update("DELETE FROM comments WHERE post_id IN (" + placeholders + ")", contentIds.toArray());
        }
        jdbc.update("DELETE FROM user_actions WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM comments WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM messages WHERE sender_id=? OR receiver_id=?", user.id(), user.id());
        jdbc.update("DELETE FROM histories WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM school_applications WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM takeout_orders WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM user_locations WHERE user_id=?", user.id());
        jdbc.update("DELETE FROM content_items WHERE owner_id=?", user.id());
        jdbc.update("UPDATE app_users SET school_id=?, profile_json=?, edited=0 WHERE id=?",
                defaultSchoolId(), defaultProfileJson(), user.id());
        return state(loadUser(user.id()));
    }

    /* ---------------- 管理端 ---------------- */
    public List<Map<String, Object>> adminApplications() {
        return jdbc.query("SELECT id, university, campus, city, contact, phone, email, canteens, note, status, created_at FROM school_applications ORDER BY created_at DESC",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("university", rs.getString("university"));
                    row.put("campus", rs.getString("campus"));
                    row.put("city", rs.getString("city"));
                    row.put("contact", rs.getString("contact"));
                    row.put("phone", rs.getString("phone"));
                    row.put("email", rs.getString("email"));
                    row.put("canteens", rs.getString("canteens"));
                    row.put("note", rs.getString("note"));
                    row.put("status", rs.getString("status"));
                    row.put("ts", formatTime(rs.getTimestamp("created_at").toInstant()));
                    return row;
                });
    }

    @Transactional
    public Map<String, Object> updateApplication(Long adminId, String id, String status) {
        if (!List.of("已通过", "已拒绝").contains(status)) throw ApiException.badRequest("审核状态仅支持已通过或已拒绝");
        String current = jdbc.queryForObject("SELECT status FROM school_applications WHERE id=?", String.class, id);
        if (current == null) throw ApiException.notFound("入驻申请不存在");
        if (!"待审核".equals(current)) throw ApiException.conflict("该申请已处理，不能重复变更");
        int updated = jdbc.update(
                "UPDATE school_applications SET status=?, updated_at=?, decided_by=?, decided_at=? WHERE id=? AND status='待审核'",
                status, Instant.now(), adminId, Instant.now(), id);
        if (updated == 0) throw ApiException.conflict("该申请已处理，不能重复变更");
        return Map.of("id", id, "status", status);
    }

    public List<Map<String, Object>> adminUsers() {
        return jdbc.query("SELECT id, email, name, role, school_id, created_at FROM app_users ORDER BY id",
                (rs, i) -> Map.of(
                        "id", rs.getLong("id"),
                        "email", rs.getString("email"),
                        "name", rs.getString("name"),
                        "role", rs.getString("role"),
                        "schoolId", rs.getString("school_id"),
                        "createdAt", formatTime(rs.getTimestamp("created_at").toInstant())
                ));
    }

    /* ---------------- 状态投影 ---------------- */
    private Map<String, Object> state(UserRow user) {
        Map<String, Object> profile = readMap(user.profileJson());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("profile", profile);
        state.put("favorites", actionTargets(user.id(), "FAVORITE_STALL", "STALL"));
        state.put("joined", actionMap(user.id(), "JOIN_MEETUP", "MEETUP"));
        state.put("likes", actionMap(user.id(), "LIKE_POST", "POST"));
        state.put("collects", actionMap(user.id(), "COLLECT_POST", "POST"));
        state.put("comments", commentsMap(user));
        state.put("greetings", greetingMap(user.id()));
        state.put("messages", messages(user.id()));
        state.put("createdMeetups", contentArray("MEETUP", user.id(), true));
        state.put("createdPosts", contentArray("POST", user.id(), true));
        state.put("checks", histories(user.id()));
        state.put("totalCheckins", 23 + countContent("POST", user.id()));
        state.put("reroll", 0);
        state.put("edited", user.edited());
        state.put("schoolId", user.schoolId());
        state.put("geo", null);
        state.put("applications", applications(user.id()));
        return state;
    }

    private List<Object> contentArray(String type) {
        return contentArray(type, null, false);
    }

    private List<Object> contentArray(String type, Long userId, boolean ownOnly) {
        return jdbc.query("SELECT type, content_id, owner_id, campus, school_id, payload_json FROM content_items WHERE type=? ORDER BY id",
                new Object[]{type},
                (rs, i) -> new ContentRow(rs.getString(1), rs.getString(2), (Long) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6)))
                .stream()
                .filter(row -> {
                    boolean mine = userId != null && userId.equals(row.ownerId());
                    return ownOnly ? mine : (userId == null || !mine);
                })
                .map(row -> decorate(type, row, userId))
                .toList();
    }

    private Object decorate(String type, ContentRow row, Long userId) {
        Map<String, Object> payload = readMap(row.payloadJson());
        if ("MEETUP".equals(type)) {
            int others = memberCount(row.contentId(), userId);
            payload.put("seat", baseSeat(payload) + others);
            if (userId != null && userId.equals(row.ownerId())) payload.put("created", true);
        }
        if ("POST".equals(type)) {
            payload.put("likes", intOf(payload.get("likes"), 0) + actionCount("LIKE_POST", "POST", row.contentId(), userId));
            payload.put("favs", intOf(payload.get("favs"), 0) + actionCount("COLLECT_POST", "POST", row.contentId(), userId));
            payload.put("comments", intOf(payload.get("comments"), 0) + commentCount(row.contentId()));
        }
        return payload;
    }

    private void putMeta(Map<String, Object> catalog, String key) {
        catalog.put(key, readValue(payloadOf("META", key)));
    }

    private Map<String, Object> findSchool(String schoolId) {
        List<String> rows = jdbc.query("SELECT payload_json FROM content_items WHERE type='SCHOOL' AND content_id=?",
                (rs, i) -> rs.getString(1), schoolId);
        return rows.isEmpty() ? null : readMap(rows.get(0));
    }

    private String campusOfSchool(String schoolId) {
        Map<String, Object> school = findSchool(schoolId);
        return school == null ? "屏峰校区" : String.valueOf(school.getOrDefault("campus", "屏峰校区"));
    }

    private void requireSameCampus(UserRow user, String campus) {
        if (!campus.equals(campusOfSchool(user.schoolId()))) {
            throw ApiException.forbidden("仅可操作当前校区内容");
        }
    }

    private ContentRow requireContentForUser(UserRow user, String type, String id) {
        ContentRow row = requireContent(type, id);
        if (row.schoolId() != null && !row.schoolId().isBlank() && !row.schoolId().equals(user.schoolId())) {
            throw ApiException.forbidden("仅可操作当前校区内容");
        }
        Map<String, Object> payload = readMap(row.payloadJson());
        requireSameCampus(user, String.valueOf(payload.getOrDefault("campus", row.campus() == null ? "" : row.campus())));
        return row;
    }

    private void requireCanteenForUser(UserRow user, String canteen) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_items WHERE type='CANTEEN' AND school_id=? AND payload_json LIKE ?",
                Integer.class, user.schoolId(), "%\"" + canteen.replace("\"", "") + "\"%");
        if (count == null || count == 0) throw ApiException.badRequest("食堂不属于当前校区");
    }

    private ContentRow requireContent(String type, String id) {
        List<ContentRow> rows = jdbc.query("SELECT type, content_id, owner_id, campus, school_id, payload_json FROM content_items WHERE type=? AND content_id=?",
                (rs, i) -> new ContentRow(rs.getString(1), rs.getString(2), (Long) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6)),
                type, id);
        if (rows.isEmpty()) throw ApiException.notFound("内容不存在");
        return rows.get(0);
    }

    private Map<String, Object> requirePayload(String type, String id) {
        return readMap(requireContent(type, id).payloadJson());
    }

    private Map<String, Object> requirePayloadForUser(UserRow user, String type, String id) {
        return readMap(requireContentForUser(user, type, id).payloadJson());
    }

    private String payloadOf(String type, String id) {
        List<String> rows = jdbc.query("SELECT payload_json FROM content_items WHERE type=? AND content_id=?",
                (rs, i) -> rs.getString(1), type, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertContent(String type, String id, Long ownerId, String campus, String schoolId, Map<String, Object> payload) {
        jdbc.update("INSERT INTO content_items(type, content_id, owner_id, campus, school_id, payload_json) VALUES(?,?,?,?,?,?)",
                type, id, ownerId, campus, schoolId, writeJson(payload));
    }

    private boolean insertAction(Long userId, String actionType, String targetType, String targetId, Map<String, Object> value) {
        if (hasAction(userId, actionType, targetType, targetId)) return false;
        try {
            jdbc.update("INSERT INTO user_actions(user_id, action_type, target_type, target_id, value_json, created_at) VALUES(?,?,?,?,?,?)",
                    userId, actionType, targetType, targetId, value == null ? null : writeJson(value), Instant.now());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private void deleteAction(Long userId, String actionType, String targetType, String targetId) {
        jdbc.update("DELETE FROM user_actions WHERE user_id=? AND action_type=? AND target_type=? AND target_id=?",
                userId, actionType, targetType, targetId);
    }

    private boolean hasAction(Long userId, String actionType, String targetType, String targetId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_actions WHERE user_id=? AND action_type=? AND target_type=? AND target_id=?",
                Integer.class, userId, actionType, targetType, targetId);
        return count != null && count > 0;
    }

    private List<String> actionTargets(Long userId, String actionType, String targetType) {
        return jdbc.query("SELECT target_id FROM user_actions WHERE user_id=? AND action_type=? AND target_type=? ORDER BY id",
                (rs, i) -> rs.getString(1), userId, actionType, targetType);
    }

    private Map<String, Object> actionMap(Long userId, String actionType, String targetType) {
        Map<String, Object> map = new LinkedHashMap<>();
        actionTargets(userId, actionType, targetType).forEach(id -> map.put(id, true));
        return map;
    }

    private int actionCount(String actionType, String targetType, String targetId, Long excludeUserId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_actions WHERE action_type=? AND target_type=? AND target_id=? AND (? IS NULL OR user_id<>?)",
                Integer.class, actionType, targetType, targetId, excludeUserId, excludeUserId);
        return count == null ? 0 : count;
    }

    private int memberCount(String meetupId, Long excludeUserId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_actions WHERE action_type='JOIN_MEETUP' AND target_type='MEETUP' AND target_id=? AND (? IS NULL OR user_id<>?)",
                Integer.class, meetupId, excludeUserId, excludeUserId);
        return count == null ? 0 : count;
    }

    private int baseSeat(Map<String, Object> payload) {
        return intOf(payload.get("seat"), 1);
    }

    private Map<String, List<Map<String, Object>>> commentsMap(UserRow user) {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        jdbc.query("SELECT c.post_id, c.author, c.text, c.created_at FROM comments c " +
                        "JOIN content_items ci ON ci.type='POST' AND ci.content_id=c.post_id " +
                        "WHERE ci.school_id=? OR ci.school_id IS NULL ORDER BY c.created_at",
                rs -> {
                    map.computeIfAbsent(rs.getString(1), key -> new ArrayList<>()).add(Map.of(
                            "user", rs.getString(2),
                            "text", rs.getString(3),
                            "ts", formatTime(rs.getTimestamp(4).toInstant())
                    ));
                }, user.schoolId());
        return map;
    }

    private int commentCount(String postId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id=?", Integer.class, postId);
        return count == null ? 0 : count;
    }

    private Map<String, Object> greetingMap(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        jdbc.query("SELECT target_id, value_json FROM user_actions WHERE user_id=? AND action_type='GREET_MEETUP'",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, userId)
                .forEach(pair -> {
                    Map<String, Object> data = pair[1] == null ? Map.of() : readMap(pair[1]);
                    map.put(pair[0], data.getOrDefault("message", ""));
                });
        return map;
    }

    private List<Map<String, Object>> messages(Long userId) {
        return jdbc.query("SELECT title, created_at FROM messages WHERE receiver_id=? ORDER BY created_at DESC",
                (rs, i) -> Map.of("title", rs.getString(1), "ts", formatTime(rs.getTimestamp(2).toInstant())), userId);
    }

    private List<Map<String, Object>> histories(Long userId) {
        return jdbc.query("SELECT type, title, icon, score, created_at FROM histories WHERE user_id=? ORDER BY id",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", rs.getString(1));
                    row.put("title", rs.getString(2));
                    row.put("icon", rs.getString(3));
                    row.put("score", rs.getString(4) == null ? "" : rs.getString(4));
                    row.put("ts", formatTime(rs.getTimestamp(5).toInstant()));
                    return row;
                }, userId);
    }

    private List<Map<String, Object>> applications(Long userId) {
        return jdbc.query("SELECT id, university, campus, city, contact, phone, email, canteens, note, status, created_at FROM school_applications WHERE user_id=? ORDER BY created_at DESC",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString(1));
                    row.put("university", rs.getString(2));
                    row.put("campus", rs.getString(3));
                    row.put("city", rs.getString(4));
                    row.put("contact", rs.getString(5));
                    row.put("phone", rs.getString(6));
                    row.put("email", rs.getString(7));
                    row.put("canteens", rs.getString(8));
                    row.put("note", rs.getString(9));
                    row.put("status", rs.getString(10));
                    row.put("ts", formatTime(rs.getTimestamp(11).toInstant()));
                    return row;
                }, userId);
    }

    private void insertHistory(Long userId, String type, String title, String icon, String score) {
        jdbc.update("INSERT INTO histories(id, user_id, type, title, icon, score, created_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), userId, type, title, icon, score, Instant.now());
    }

    private void insertMessage(Long receiverId, Long senderId, String title) {
        jdbc.update("INSERT INTO messages(id, user_id, sender_id, receiver_id, title, created_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), receiverId, senderId, receiverId, title, Instant.now());
    }

    private int countContent(String type, Long ownerId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM content_items WHERE type=? AND owner_id=?", Integer.class, type, ownerId);
        return count == null ? 0 : count;
    }

    private UserRow findUserByEmail(String email) {
        List<UserRow> rows = jdbc.query("SELECT id, email, password_hash, name, role, school_id, profile_json, edited FROM app_users WHERE email=?",
                (rs, i) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)),
                email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UserRow loadUser(Long id) {
        List<UserRow> rows = jdbc.query("SELECT id, email, password_hash, name, role, school_id, profile_json, edited FROM app_users WHERE id=?",
                (rs, i) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)),
                id);
        if (rows.isEmpty()) throw ApiException.unauthorized("用户不存在");
        return rows.get(0);
    }

    private boolean existsEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email=?", Integer.class, email);
        return count != null && count > 0;
    }

    private String defaultSchoolId() {
        Map<String, Object> school = readMap(payloadOf("META", "school"));
        return String.valueOf(school.getOrDefault("id", "zjut-pingfeng"));
    }

    private String defaultProfileJson() {
        return payloadOf("META", "profile") == null ? "{}" : payloadOf("META", "profile");
    }

    private String userName(UserRow user) {
        return user.name() == null || user.name().isBlank() ? user.email() : user.name();
    }

    private List<String> buildPostTags(List<String> tags, int price) {
        List<String> result = new ArrayList<>();
        result.add("#" + tags.get(0));
        tags.stream().skip(1).forEach(result::add);
        result.add("人均 " + price);
        return result;
    }

    private int intOf(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String formatTime(Instant instant) {
        return TIME_FORMAT.format(instant);
    }

    private String timeLabel() {
        return formatTime(Instant.now());
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("数据格式错误");
        }
    }

    private Object readValue(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<String, Object>();
        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.convertValue(node, Object.class);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("数据序列化失败");
        }
    }
}
