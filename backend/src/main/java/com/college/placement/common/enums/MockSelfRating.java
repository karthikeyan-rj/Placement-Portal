package com.college.placement.common.enums;

/**
 * The student's own judgement of a descriptive answer, captured after the fact.
 *
 * <p>This is deliberately self-reported. It is the only "grading" signal the
 * feature records, because the preparation bank is entirely descriptive and
 * cannot be scored objectively.
 */
public enum MockSelfRating {
    NEED_PRACTICE,
    PARTIALLY_CONFIDENT,
    CONFIDENT
}
