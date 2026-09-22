package com.college.placement.resume.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single recognised resume section and whether it was found in the resume. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionCheck {

    private String name;
    private boolean found;
}