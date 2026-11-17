package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.ClaimDocument;
import com.nabeelsayyed.benefitsportal.model.ClaimEventType;
import com.nabeelsayyed.benefitsportal.model.DocumentType;
import com.nabeelsayyed.benefitsportal.repository.ClaimDocumentRepository;
import com.nabeelsayyed.benefitsportal.storage.DocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Uploading and downloading proof documents (pay stubs, layoff letters, ID).
 *
 * Uploads from the public internet are a classic way to attack a system, so this is careful:
 * - Only PDF, PNG and JPEG are allowed, and the type is checked from the file's first bytes
 *   (its "magic number"), not from the name or what the browser says, since both can lie.
 * - Files have a size limit and each claim has a file count limit.
 * - The storage key is made by the app (claims/{id}/{random}.pdf), so a file name like
 *   "../../secret" can never decide where a file goes.
 */
@Service
public class DocumentService {

    static final int MAX_DOCUMENTS_PER_CLAIM = 10;

    private final ClaimDocumentRepository documentRepository;
    private final DocumentStorage storage;
    private final ClaimService claimService;
    private final AuditService auditService;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Value("${documents.max-bytes:5242880}")
    private long maxBytes;

    public DocumentService(ClaimDocumentRepository documentRepository, DocumentStorage storage,
                           ClaimService claimService, AuditService auditService,
                           CurrentActor currentActor, Clock clock) {
        this.documentRepository = documentRepository;
        this.storage = storage;
        this.claimService = claimService;
        this.auditService = auditService;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** A downloaded document: its record plus the file's bytes. */
    public record DocumentContent(ClaimDocument document, byte[] bytes) {
    }

    @Transactional
    public ClaimDocument upload(Long claimId, DocumentType type, MultipartFile file) {
        claimService.findById(claimId); // 404 if the claim doesn't exist
        if (type == null) {
            throw new InvalidClaimActionException("Pick what kind of document this is.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidClaimActionException("Choose a file to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidClaimActionException("Files can be up to " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        if (documentRepository.countByClaimId(claimId) >= MAX_DOCUMENTS_PER_CLAIM) {
            throw new InvalidClaimActionException("A claim can have up to " + MAX_DOCUMENTS_PER_CLAIM + " documents.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileKind kind = FileKind.detect(bytes);
        if (kind == null) {
            throw new InvalidClaimActionException("Only PDF, PNG and JPEG files can be uploaded.");
        }

        String key = "claims/" + claimId + "/" + UUID.randomUUID() + "." + kind.extension;
        storage.put(key, bytes, kind.contentType);

        String name = cleanFilename(file.getOriginalFilename());
        ClaimDocument saved = documentRepository.save(new ClaimDocument(claimId, type, name, kind.contentType,
                bytes.length, key, currentActor.name(), LocalDateTime.now(clock)));
        auditService.record(claimId, ClaimEventType.DOCUMENT_UPLOADED, null, null,
                type.getLabel() + ": " + name);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClaimDocument> documentsFor(Long claimId) {
        return documentRepository.findAllByClaimIdOrderByUploadedAtDesc(claimId);
    }

    /** Fetches a document, checking that it really belongs to this claim. */
    @Transactional(readOnly = true)
    public DocumentContent download(Long claimId, Long documentId) {
        ClaimDocument document = documentRepository.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new InvalidClaimActionException("No such document on this claim."));
        return new DocumentContent(document, storage.get(document.getStorageKey()));
    }

    /** Keeps only the last part of the name and swaps unusual characters for "_". */
    static String cleanFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }

    /** The file types we accept, and the first bytes that every file of that type starts with. */
    enum FileKind {
        PDF("application/pdf", "pdf", new byte[]{0x25, 0x50, 0x44, 0x46}),                 // "%PDF"
        PNG("image/png", "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        final String contentType;
        final String extension;
        private final byte[] magic;

        FileKind(String contentType, String extension, byte[] magic) {
            this.contentType = contentType;
            this.extension = extension;
            this.magic = magic;
        }

        static FileKind detect(byte[] bytes) {
            for (FileKind kind : values()) {
                if (bytes.length >= kind.magic.length && startsWith(bytes, kind.magic)) {
                    return kind;
                }
            }
            return null;
        }

        private static boolean startsWith(byte[] bytes, byte[] prefix) {
            for (int i = 0; i < prefix.length; i++) {
                if (bytes[i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
