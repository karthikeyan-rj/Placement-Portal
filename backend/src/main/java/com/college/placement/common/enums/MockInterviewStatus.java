package com.college.placement.common.enums;

/** Lifecycle of a Mock Interview session. */
public enum MockInterviewStatus {
    /** Created and still answerable. At most one per student (DB-enforced). */
    IN_PROGRESS,
    /** Finished by the student. Immutable afterwards, except self-rating. */
    COMPLETED,
    /** Given up by the student. Kept as history, never deleted. */
    ABANDONED
}
