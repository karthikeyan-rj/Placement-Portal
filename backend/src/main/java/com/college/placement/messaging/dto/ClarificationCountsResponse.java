package com.college.placement.messaging.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClarificationCountsResponse {
    private long total;
    private long open;
    private long answered;
}