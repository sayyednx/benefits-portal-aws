package com.nabeelsayyed.benefitsportal.dto;

import com.nabeelsayyed.benefitsportal.model.SeparationReason;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What a citizen submits when filing a new claim, via the web form or the JSON API. */
public class ClaimRequest {

    @NotBlank(message = "Enter your full name")
    private String claimantName;

    @NotBlank(message = "Enter an email address")
    @Email(message = "Enter a valid email address")
    private String claimantEmail;

    @NotBlank(message = "Enter your former employer's name")
    private String employerName;

    @NotNull(message = "Enter your last day worked")
    @PastOrPresent(message = "Last day worked can't be in the future")
    private LocalDate lastDayWorked;

    @NotNull(message = "Select a reason for separation")
    private SeparationReason separationReason;

    @NotNull(message = "Enter your average weekly wage")
    @DecimalMin(value = "0.0", inclusive = false, message = "Average weekly wage must be greater than 0")
    private BigDecimal averageWeeklyWage;

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public void setClaimantEmail(String claimantEmail) {
        this.claimantEmail = claimantEmail;
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public LocalDate getLastDayWorked() {
        return lastDayWorked;
    }

    public void setLastDayWorked(LocalDate lastDayWorked) {
        this.lastDayWorked = lastDayWorked;
    }

    public SeparationReason getSeparationReason() {
        return separationReason;
    }

    public void setSeparationReason(SeparationReason separationReason) {
        this.separationReason = separationReason;
    }

    public BigDecimal getAverageWeeklyWage() {
        return averageWeeklyWage;
    }

    public void setAverageWeeklyWage(BigDecimal averageWeeklyWage) {
        this.averageWeeklyWage = averageWeeklyWage;
    }
}
