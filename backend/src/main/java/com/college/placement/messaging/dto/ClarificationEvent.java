package com.college.placement.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight revalidation signal pushed over the existing messaging SSE stream
 * when a clarification thread changes.
 *
 * <p>This is a <strong>revalidation hint only</strong>: it deliberately carries no
 * clarification body, no recipient list, no email addresses and no student
 * details. Clients must re-read the authoritative data through the normal
 * clarification endpoints, which apply the existing 7P.3 authorization rules.
 *
 * <p>These events never affect the unread-message badge or red dot; that state
 * remains exclusively driven by {@code NEW_MESSAGE}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClarificationEvent {
    private String type;
    private Long messageId;
    private Long threadId;
}
