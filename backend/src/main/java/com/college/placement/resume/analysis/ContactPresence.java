package com.college.placement.resume.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Presence of professional contact signals. Only booleans are exposed/serialised;
 * actual email addresses or phone numbers are never surfaced by the API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactPresence {

    private boolean emailPresent;
    private boolean phonePresent;
    private boolean linkedinPresent;
    private boolean githubPresent;
    private boolean portfolioPresent;

    public int foundCount() {
        int count = 0;
        if (emailPresent) count++;
        if (phonePresent) count++;
        if (linkedinPresent) count++;
        if (githubPresent) count++;
        if (portfolioPresent) count++;
        return count;
    }
}