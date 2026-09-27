package com.college.placement.placement.dto;

/** Minimal projection of an email-eligible Placement Drive recipient. */
public interface DriveRecipientProjection {

    Long getUserId();

    String getEmail();
}
