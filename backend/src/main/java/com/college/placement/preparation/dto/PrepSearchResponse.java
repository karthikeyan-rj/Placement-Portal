package com.college.placement.preparation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepSearchResponse {
    private List<PrepSearchHit> modules;
    private List<PrepSearchHit> topics;
    private List<PrepSearchHit> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrepSearchHit {
        private Long id;
        private String title;
        private String context;
    }
}