package com.nabeelsayyed.benefitsportal.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests share one in-memory database, and the fraud checks flag claims that share an email
 * or a name and employer. Tests that aren't about fraud use this to keep their claims unique.
 */
public final class TestData {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestData() {
    }

    /** A short label that is different every time, like "c17". */
    public static String unique() {
        return "c" + COUNTER.incrementAndGet();
    }
}
