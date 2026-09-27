package com.college.placement.common.enums;

/**
 * Difficulty filter chosen when starting a session.
 *
 * <p>{@link #MIXED} means "no difficulty restriction" and is a filter value only;
 * it is never matched against {@link PrepDifficulty}, which holds the real
 * EASY/MEDIUM/HARD metadata authored on each preparation question.
 */
public enum MockDifficultyFilter {
    EASY,
    MEDIUM,
    HARD,
    MIXED
}
