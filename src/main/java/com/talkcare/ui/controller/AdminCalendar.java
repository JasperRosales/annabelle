package com.talkcare.ui.controller;

import com.talkcare.ui.dom.Dom;
import com.talkcare.ui.dom.El;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** The admin "Schedule Overview" month grid; days with appointments are highlighted. */
final class AdminCalendar {
    private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final Dom dom;
    private final YearMonth month = YearMonth.now();

    AdminCalendar(Dom dom) {
        this.dom = dom;
    }

    YearMonth month() {
        return month;
    }

    void render(Set<Integer> appointmentDays) {
        dom.findById("admin-calendar-month").ifPresent(label -> label.text(MONTH.format(month)));
        El grid = dom.byId("admin-calendar-grid").clear();
        for (String day : WEEKDAYS) grid.append(dom.create("div", "calendar-weekday", day));

        // Sunday-first grid: Sunday → 0 blanks, Monday → 1, ... Saturday → 6.
        int blanks = month.atDay(1).getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : month.atDay(1).getDayOfWeek().getValue();
        for (int i = 0; i < blanks; i++) grid.append(dom.create("div", "calendar-cell calendar-cell-empty"));

        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            El cell = dom.create("div", "calendar-cell", String.valueOf(day));
            if (appointmentDays.contains(day)) cell.addClass("has-appointment");
            grid.append(cell);
        }
    }
}
