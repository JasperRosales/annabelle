package com.talkcare.ui.controller;

import com.talkcare.ui.dom.Dom;
import com.talkcare.ui.dom.El;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * The admin schedule month grid: days with sessions are marked, today is outlined, and a day
 * can be selected ({@code data-action="admin-calendar-day"}) to list its sessions.
 */
final class AdminCalendar {
    private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final Dom dom;
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();

    AdminCalendar(Dom dom) {
        this.dom = dom;
    }

    YearMonth month() {
        return month;
    }

    LocalDate selected() {
        return selected;
    }

    /** Moves by {@code months}; selects today in the current month, otherwise the 1st. */
    void shift(int months) {
        month = month.plusMonths(months);
        selected = month.equals(YearMonth.now()) ? LocalDate.now() : month.atDay(1);
    }

    void select(int day) {
        selected = month.atDay(day);
    }

    void render(Set<Integer> appointmentDays) {
        dom.findById("admin-calendar-month").ifPresent(label -> label.text(MONTH.format(month)));
        El grid = dom.byId("admin-calendar-grid").clear();
        for (String day : WEEKDAYS) grid.append(dom.create("div", "calendar-weekday", day));

        // Sunday-first grid: Sunday → 0 blanks, Monday → 1, ... Saturday → 6.
        DayOfWeek first = month.atDay(1).getDayOfWeek();
        int blanks = first == DayOfWeek.SUNDAY ? 0 : first.getValue();
        for (int i = 0; i < blanks; i++) grid.append(dom.create("div", "calendar-cell calendar-cell-empty"));

        LocalDate today = LocalDate.now();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            El cell = dom.create("button", "calendar-cell", String.valueOf(day))
                    .attr("type", "button")
                    .attr("data-action", "admin-calendar-day")
                    .attr("data-arg", String.valueOf(day));
            if (appointmentDays.contains(day)) cell.addClass("has-appointment");
            if (date.equals(today)) cell.addClass("today");
            if (date.equals(selected)) cell.addClass("selected");
            grid.append(cell);
        }
    }
}
