package com.college.placement.messaging.dto;

import com.college.placement.messaging.ClarificationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClarificationResponse {
    private Long threadId;
    private Long messageId;
    private String messageTitle;
    private Long requesterId;
    private String requesterName;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private ClarificationStatus status;
    private String createdAt;
    private String updatedAt;
    private List<ClarificationEntryResponse> entries;
    private long totalEntries;
}