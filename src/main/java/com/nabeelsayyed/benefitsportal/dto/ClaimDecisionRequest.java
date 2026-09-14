package com.nabeelsayyed.benefitsportal.dto;

import com.nabeelsayyed.benefitsportal.model.ClaimStatus;
import jakarta.validation.constraints.NotNull;

/** What an admin submits to approve, deny, or move a claim into review. */
public class ClaimDecisionRequest {

    @NotNull(message = "Select a status")
    private ClaimStatus status;

    private String reviewerNotes;

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }
}
