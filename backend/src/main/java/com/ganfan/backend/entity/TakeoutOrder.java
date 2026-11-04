package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "takeout_orders")
public class TakeoutOrder {
    @Id @Column(length = 40)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "takeout_id", nullable = false, length = 100)
    private String takeoutId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTakeoutId() { return takeoutId; }
    public void setTakeoutId(String takeoutId) { this.takeoutId = takeoutId; }
}
