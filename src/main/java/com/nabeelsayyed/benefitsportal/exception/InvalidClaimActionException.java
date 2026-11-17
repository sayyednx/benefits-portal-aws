package com.nabeelsayyed.benefitsportal.exception;

/**
 * Thrown when someone tries to do something the program's rules don't allow, such as
 * approving a claim that was never reviewed or appealing after the deadline.
 * The message is written so it can be shown straight to the person.
 */
public class InvalidClaimActionException extends RuntimeException {
    public InvalidClaimActionException(String message) {
        super(message);
    }
}
