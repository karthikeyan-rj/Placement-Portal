package com.college.placement.messaging.email;

public enum EmailOutboxStatus {
    PENDING,
    IN_FLIGHT,
    /** Provider accepted the message. This is NOT proof of inbox delivery. */
    SUBMITTED,
    /** Confirmed by a provider delivery event. */
    DELIVERED,
    /** Provider reported a temporary delay; still deliverable. */
    DELAYED,
    /** Hard bounce reported by the provider. */
    BOUNCED,
    /** Spam complaint reported by the provider. */
    COMPLAINED,
    /** Provider suppressed the message and will not attempt delivery again. */
    SUPPRESSED,
    /** Legacy value written before SUBMITTED existed; treated as provider-accepted. */
    SENT,
    FAILED,
    SKIPPED_INVALID_EMAIL,
    CONFIG_ERROR
}