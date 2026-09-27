package com.college.placement.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregate, non-identifying delivery status for one Placement Drive notification batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveEmailStatusResponse {
    private Long driveId;
    private String eventKey;
    /** Recipients resolved by the authoritative eligibility rules. */
    private long eligibleRecipients;
    /** Outbox rows created for this event (excludes recipients skipped for a bad address). */
    private long queued;
    private long pending;
    private long submitted;
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
