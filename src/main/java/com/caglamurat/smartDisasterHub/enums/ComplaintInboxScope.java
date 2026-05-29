package com.caglamurat.smartDisasterHub.enums;

/**
 * Filters for the staff complaint inbox.
 */
public enum ComplaintInboxScope {
    /** Active complaints assigned to the current staff member. */
    MINE,
    /** Active complaints with no assignee (claim pool). */
    UNASSIGNED,
    /** All active complaints (admin only). */
    ALL,
    /** Resolved or closed complaints (staff). */
    RESOLVED
}
