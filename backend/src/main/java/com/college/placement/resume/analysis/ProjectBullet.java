package com.college.placement.resume.analysis;

/**
 * Quality assessment of a single bullet (project/experience/achievement line).
 * All flags are derived from simple, documented rules.
 */
public record ProjectBullet(
        String text,
        boolean startsWithActionVerb,
        boolean measurable,
        boolean vague,
        boolean firstPerson,
        boolean tooLong,
        boolean mentionsTechnology) {
}