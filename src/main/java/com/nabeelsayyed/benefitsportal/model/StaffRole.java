package com.nabeelsayyed.benefitsportal.model;

/**
 * What a staff member is allowed to do.
 *
 * - CASEWORKER: reviews new claims and approves or denies them.
 * - SUPERVISOR: everything a caseworker can do, plus deciding appeals and managing staff accounts.
 */
public enum StaffRole {
    CASEWORKER,
    SUPERVISOR
}
