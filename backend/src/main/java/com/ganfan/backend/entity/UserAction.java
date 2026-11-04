package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_actions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "action_type", "target_type", "target_id"}))
public class UserAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;
    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;
    @Lob @Column(name = "value_json")
    private String valueJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public Instant getCreatedAt() { return createdAt; }
}
