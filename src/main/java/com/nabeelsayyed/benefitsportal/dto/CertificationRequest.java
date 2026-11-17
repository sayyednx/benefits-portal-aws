package com.nabeelsayyed.benefitsportal.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The claimant's answers for one week. */
public class CertificationRequest {

    @NotNull(message = "Pick the week you are certifying for")
    private LocalDate weekEnding;

    @NotNull(message = "Tell us if you were able and available to work")
    private Boolean ableAndAvailable;

    @NotNull(message = "Tell us if you refused any job offer")
    private Boolean refusedWork;

    @NotNull(message = "Enter how many employers you contacted")
    @Min(value = 0, message = "Job contacts can't be negative")
    @Max(value = 50, message = "Enter 50 or fewer job contacts")
    private Integer jobContacts;

    @NotNull(message = "Enter what you earned this week (0 if nothing)")
    @DecimalMin(value = "0.0", message = "Earnings can't be negative")
    private BigDecimal grossEarnings;

    public LocalDate getWeekEnding() { return weekEnding; }
    public void setWeekEnding(LocalDate weekEnding) { this.weekEnding = weekEnding; }
    public Boolean getAbleAndAvailable() { return ableAndAvailable; }
    public void setAbleAndAvailable(Boolean ableAndAvailable) { this.ableAndAvailable = ableAndAvailable; }
    public Boolean getRefusedWork() { return refusedWork; }
    public void setRefusedWork(Boolean refusedWork) { this.refusedWork = refusedWork; }
    public Integer getJobContacts() { return jobContacts; }
    public void setJobContacts(Integer jobContacts) { this.jobContacts = jobContacts; }
    public BigDecimal getGrossEarnings() { return grossEarnings; }
    public void setGrossEarnings(BigDecimal grossEarnings) { this.grossEarnings = grossEarnings; }
}
