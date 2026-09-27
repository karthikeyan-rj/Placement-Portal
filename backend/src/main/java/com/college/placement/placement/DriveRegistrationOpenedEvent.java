package com.college.placement.placement;

/**
 * Signals that a drive entered REGISTRATION_OPEN. The notification fanout is handled only
 * after the surrounding transaction commits, so email problems can never roll back the
 * drive status change.
 */
public record DriveRegistrationOpenedEvent(Long driveId) {
}
