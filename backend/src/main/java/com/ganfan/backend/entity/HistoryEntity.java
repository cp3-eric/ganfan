package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "histories")
public class HistoryEntity {
    @Id @Column(length = 40)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(length = 30)
    private String icon;
    @Column(length = 50)
    private String score;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }
    public Instant getCreatedAt() { return createdAt; }
}
