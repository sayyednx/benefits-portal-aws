package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * A staff account. Each person gets their own login, so the audit history can say exactly
 * who made each decision. Accounts are switched off (active = false) instead of deleted,
 * so old history lines still point at a real account.
 */
@Entity
@Table(name = "staff_users")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** A one-way hash of the password (BCrypt). The real password is never stored. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected StaffUser() {
        // JPA
    }

    public StaffUser(String username, String passwordHash, String fullName, StaffRole role, LocalDateTime createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public StaffRole getRole() { return role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
