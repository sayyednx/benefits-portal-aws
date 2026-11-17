package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.dto.ClaimRequest;
import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.Claim;
import com.nabeelsayyed.benefitsportal.model.ClaimDocument;
import com.nabeelsayyed.benefitsportal.model.ClaimEventType;
import com.nabeelsayyed.benefitsportal.model.DocumentType;
import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DocumentServiceTest {

    static final byte[] TINY_PDF = "%PDF-1.4\n% a tiny test file\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    static final byte[] TINY_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private ClaimService claimService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AuditService auditService;

    private Claim fileClaim() {
        ClaimRequest request = new ClaimRequest();
        request.setClaimantName("Robin Ellis");
        request.setClaimantEmail("robin@example.com");
        request.setEmployerName("Greenway Grocers");
        request.setLastDayWorked(LocalDate.now().minusDays(4));
        request.setSeparationReason(SeparationReason.LAID_OFF);
        request.setAverageWeeklyWage(new BigDecimal("500.00"));
        return claimService.fileClaim(request);
    }

    @Test
    void aPdfIsSavedAndCanBeReadBack() {
        Claim claim = fileClaim();
        var file = new MockMultipartFile("file", "layoff letter.pdf", "application/pdf", TINY_PDF);

        ClaimDocument doc = documentService.upload(claim.getId(), DocumentType.SEPARATION_LETTER, file);
        var content = documentService.download(claim.getId(), doc.getId());

        assertThat(doc.getContentType()).isEqualTo("application/pdf");
        assertThat(doc.getStorageKey()).startsWith("claims/" + claim.getId() + "/").endsWith(".pdf");
        assertThat(content.bytes()).isEqualTo(TINY_PDF);
        assertThat(auditService.historyFor(claim.getId()))
                .anyMatch(e -> e.getType() == ClaimEventType.DOCUMENT_UPLOADED);
    }

    @Test
    void theRealTypeComesFromTheBytesNotTheName() {
        Claim claim = fileClaim();
        // Named like a PDF and claims to be a PDF, but it's really a PNG image.
        var file = new MockMultipartFile("file", "paystub.pdf", "application/pdf", TINY_PNG);

        ClaimDocument doc = documentService.upload(claim.getId(), DocumentType.PAY_STUB, file);

        assertThat(doc.getContentType()).isEqualTo("image/png");
        assertThat(doc.getStorageKey()).endsWith(".png");
    }

    @Test
    void aProgramDisguisedAsAPdfIsRefused() {
        Claim claim = fileClaim();
        byte[] windowsProgram = {'M', 'Z', (byte) 0x90, 0, 3, 0};
        var file = new MockMultipartFile("file", "paystub.pdf", "application/pdf", windowsProgram);

        assertThatThrownBy(() -> documentService.upload(claim.getId(), DocumentType.PAY_STUB, file))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("Only PDF, PNG and JPEG");
    }

    @Test
    void aFileOverTheLimitIsRefused() {
        Claim claim = fileClaim();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(TINY_PDF, 0, big, 0, TINY_PDF.length);
        var file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> documentService.upload(claim.getId(), DocumentType.OTHER, file))
                .isInstanceOf(InvalidClaimActionException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void aDocumentCantBeFetchedThroughSomeoneElsesClaim() {
        Claim mine = fileClaim();
        Claim theirs = fileClaim();
        var file = new MockMultipartFile("file", "id.pdf", "application/pdf", TINY_PDF);
        ClaimDocument doc = documentService.upload(mine.getId(), DocumentType.PHOTO_ID, file);

        assertThatThrownBy(() -> documentService.download(theirs.getId(), doc.getId()))
                .isInstanceOf(InvalidClaimActionException.class);
    }

    @Test
    void fileNamesAreCleanedUp() {
        assertThat(DocumentService.cleanFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.cleanFilename("C:\\Users\\me\\pay stub (1).pdf")).isEqualTo("pay stub _1_.pdf");
        assertThat(DocumentService.cleanFilename("<script>.pdf")).isEqualTo("_script_.pdf");
        assertThat(DocumentService.cleanFilename(null)).isEqualTo("document");
    }
}
