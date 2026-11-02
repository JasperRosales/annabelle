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
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", java.util.Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US);
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US);
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

    /** Month abbreviation and day for a date tile: {"NOV", "5"}; dashes when missing. */
    public static String[] dayTile(String iso) {
        return parse(iso).map(t -> t.atZone(ZoneId.systemDefault()))
                .map(z -> new String[] {MONTH.format(z).toUpperCase(), String.valueOf(z.getDayOfMonth())})
                .orElse(new String[] {"—", "—"});
    }

    /** "Thu, Nov 5 · 2:00 – 3:00 PM". */
    public static String timeRange(String startIso, String endIso) {
        Optional<Instant> start = parse(startIso);
        if (start.isEmpty()) return "—";
        var s = start.get().atZone(ZoneId.systemDefault());
        String text = DAY.format(s) + " · " + SHORT_TIME.format(s);
        return parse(endIso).map(e -> text + " – " + SHORT_TIME.format(e.atZone(ZoneId.systemDefault()))).orElse(text);
    }

    /** "5 min ago", "3 h ago", "Yesterday", or the date. */
    public static String relative(String iso) {
        Optional<Instant> when = parse(iso);
        if (when.isEmpty()) return "";
        long minutes = java.time.Duration.between(when.get(), Instant.now()).toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (minutes < 24 * 60) return (minutes / 60) + " h ago";
        if (minutes < 48 * 60) return "Yesterday";
        return DATE.format(when.get().atZone(ZoneId.systemDefault()));
    }

    /** True when the timestamp is in the future. */
    public static boolean isFuture(String iso) {
        return parse(iso).map(t -> t.isAfter(Instant.now())).orElse(false);
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
