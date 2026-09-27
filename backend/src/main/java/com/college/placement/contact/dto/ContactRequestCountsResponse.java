package com.college.placement.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-status totals for one contact-request view, computed with a single
 * grouped query so the page never loads every row just to show a counter.
 * {@code rejected} is presented to users as "Declined".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestCountsResponse {
    private long pending;
    private long accepted;
    private long rejected;
    private long resolved;
    private long total;
}
