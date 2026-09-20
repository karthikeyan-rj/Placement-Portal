package com.college.placement.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryResponse {
    private long totalStudentPopulation;
    private long placementInterested;
    private long placed;
    private long notPlaced;
    private long blocked;
    private double placementRate;
    private long activeDrives;
    private long completedDrives;
    private long activeCompanies;
    private List<DepartmentRow> byDepartment;
    private List<BatchRow> byBatch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentRow {
        private Long departmentId;
        private String departmentName;
        private long studentCount;
        private long interestedCount;
        private long placedCount;
        private double placementRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchRow {
        private String batch;
        private long studentCount;
        private long interestedCount;
        private long placedCount;
        private double placementRate;
    }
}