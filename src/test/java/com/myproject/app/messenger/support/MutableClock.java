package com.myproject.app.messenger.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

    public static final Instant DEFAULT = Instant.parse("2026-10-05T09:00:00Z");

    private volatile Instant now = DEFAULT;

    public void advance(Duration d) { now = now.plus(d); }
    public void reset() { now = DEFAULT; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
