package com.college.placement.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageNotification {
    private String type;
    private Long messageId;
    private String senderName;
    private String title;
    private String createdAt;
}
