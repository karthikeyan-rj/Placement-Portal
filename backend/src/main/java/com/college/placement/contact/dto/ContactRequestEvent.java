package com.college.placement.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata-only realtime payload for contact-request lifecycle events. It
 * deliberately carries no subject, message body, email address or any other
 * request content: the owning client re-reads the authorized list it already
 * has access to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestEvent {
    private String type;
    private Long requestId;
}
