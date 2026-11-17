package com.nabeelsayyed.benefitsportal.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Information about one uploaded file. The file itself lives in DocumentStorage (S3 in AWS);
 * the database only keeps this record of it.
 */
@Entity
@Table(name = "claim_documents", indexes = @Index(name = "idx_documents_claim", columnList = "claimId"))
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private DocumentType type;

    /** The file name the claimant uploaded, cleaned up. Only for display. */
    @Column(nullable = false, updatable = false)
    private String originalFilename;

    /** The real file type, worked out from the file's contents (not what the browser claimed). */
    @Column(nullable = false, updatable = false, length = 50)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    /** Where the file is in storage. Made by the app, never from the claimant's file name. */
    @Column(nullable = false, updatable = false, unique = true)
    private String storageKey;

    @Column(nullable = false, updatable = false, length = 100)
    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected ClaimDocument() {
        // JPA
    }

    public ClaimDocument(Long claimId, DocumentType type, String originalFilename, String contentType,
                         long sizeBytes, String storageKey, String uploadedBy, LocalDateTime uploadedAt) {
        this.claimId = claimId;
        this.type = type;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public DocumentType getType() { return type; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
