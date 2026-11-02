package com.talkcare.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Days of a month that have appointments (GET /api/admin/schedule). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Schedule(int year, int month, List<Integer> days) {}
