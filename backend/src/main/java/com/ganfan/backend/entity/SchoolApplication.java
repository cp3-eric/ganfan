package com.ganfan.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "school_applications")
public class SchoolApplication {
    @Id @Column(length = 40)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String university;
    @Column(nullable = false, length = 100)
    private String campus;
    @Column(nullable = false, length = 80)
    private String city;
    @Column(nullable = false, length = 80)
    private String contact;
    @Column(nullable = false, length = 40)
    private String phone;
    @Column(length = 120)
    private String email;
    @Column(length = 40)
    private String canteens;
    @Column(length = 500)
    private String note;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUniversity() { return university; }
    public void setUniversity(String university) { this.university = university; }
    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCanteens() { return canteens; }
    public void setCanteens(String canteens) { this.canteens = canteens; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
