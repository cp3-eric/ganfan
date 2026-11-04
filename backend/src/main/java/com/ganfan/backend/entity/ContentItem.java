package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_items", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "content_id"}))
public class ContentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(name = "content_id", nullable = false, length = 100)
    private String contentId;
    @Column(name = "owner_id")
    private Long ownerId;
    @Column(length = 80)
    private String campus;
    @Column(name = "school_id", length = 80)
    private String schoolId;
    @Lob @Column(name = "payload_json", nullable = false)
    private String payloadJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
    public String getSchoolId() { return schoolId; }
    public void setSchoolId(String schoolId) { this.schoolId = schoolId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
