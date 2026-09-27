package com.college.placement.common.enums;

/**
 * Interview mode for a Mock Interview session.
 *
 * <p>Each constant resolves to a set of preparation modules at runtime (see
 * {@code MockInterviewModeCatalog}), so the module list always reflects what the
 * preparation domain actually contains instead of being hardcoded in the UI.
 */
public enum MockInterviewType {
    TECHNICAL,
    HR_BEHAVIORAL,
    MIXED
}
