package com.nabeelsayyed.benefitsportal.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Makes the number a claimant uses to look up their claim, like "UI-7K3F-9QX2".
 *
 * Why not just use the database id (1, 2, 3...)? Because ids are easy to guess: anyone could
 * try 1, 2, 3 and see other people's claims. These numbers are random (SecureRandom, the
 * kind meant for security), and a lookup also needs the claimant's email.
 * The letters skip 0/O and 1/I so people don't mix them up when reading them out on a call.
 */
@Component
public class ConfirmationNumberGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        return "UI-" + block() + "-" + block();
    }

    private String block() {
        char[] chars = new char[4];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    /** Tidies what a person typed: trims spaces and makes letters uppercase. */
    public static String normalize(String typed) {
        return typed == null ? "" : typed.trim().toUpperCase();
    }
}
