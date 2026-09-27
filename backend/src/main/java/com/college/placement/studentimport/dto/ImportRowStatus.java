package com.college.placement.studentimport.dto;

/**
 * Per-row classification for PO student CSV import. READY rows may be
 * imported; every other status is reported and skipped with a reason.
 */
public enum ImportRowStatus {
    READY,
    DUPLICATE,
    ALREADY_REGISTERED,
    ALREADY_AUTHORIZED,
    INVALID
}
