package com.nabeelsayyed.benefitsportal.exception;

public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(Long id) {
        super("No claim found with id " + id);
    }
}
