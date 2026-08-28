package com.college.placement.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;
    private String senderName;
    private String senderRole;
    private String title;
    private String content;
    private String messageType;
    private String createdAt;
    private int totalRecipients;
    private int deliveredCount;
    private int readCount;
    private int upvoteCount;
    private int downvoteCount;
}
