package com.nabeelsayyed.benefitsportal.support;

import java.time.*;

/** A clock tests can move forward by hand, to check deadlines without waiting real days. */
public class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        this.now = start.atZone(zone).toInstant();
    }

    public void setTo(LocalDateTime time) {
        this.now = time.atZone(zone).toInstant();
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(LocalDateTime.ofInstant(now, zone), zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
