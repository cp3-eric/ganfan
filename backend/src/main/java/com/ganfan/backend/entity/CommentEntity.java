package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class CommentEntity {
    @Id @Column(length = 40)
    private String id;
    @Column(name = "post_id", nullable = false, length = 100)
    private String postId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 80)
    private String author;
    @Column(nullable = false, length = 500)
    private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
}
