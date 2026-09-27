package com.college.placement.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatusResponse {
    private long pending;
    /** Provider accepted the message. Not proof of inbox delivery. */
    private long submitted;
    /** Confirmed by a provider delivery event. */
    private long delivered;
    private long delayed;
    private long bounced;
    private long complained;
    private long suppressed;
    private long failed;
    private long skippedInvalid;
    private long configError;
    private long total;
}