package com.talkcare.ui;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Optional;

/** Date/time display for API timestamps (ISO-8601, UTC), in the computer's time zone. */
public final class Formats {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);

    private Formats() {}

    /** "02:30 PM", or "Just now" for missing/unknown values (chat bubbles). */
    public static String time(String iso) {
        return parse(iso).map(t -> TIME.format(t.atZone(ZoneId.systemDefault()))).orElse("Just now");
    }

    /** "Nov 2, 2026, 2:30 PM", "—" when missing, or the raw value when it is not a timestamp. */
    public static String dateTime(String iso) {
        if (iso == null || iso.isBlank()) return "—";
        return parse(iso).map(t -> DATE_TIME.format(t.atZone(ZoneId.systemDefault()))).orElse(iso);
    }

    private static Optional<Instant> parse(String iso) {
        if (iso == null || iso.isBlank()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeParseException e) {
            try {
                return Optional.of(Instant.parse(iso));
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
    }
}
