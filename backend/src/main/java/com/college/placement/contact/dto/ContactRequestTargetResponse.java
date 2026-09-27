package com.college.placement.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A coordinator the current user is actually allowed to send a contact
 * request to. The list is resolved server-side from the caller's department
 * and never includes PO accounts or inactive users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestTargetResponse {
    private Long id;
    private String name;
    private String role;
    private String departmentName;
}
