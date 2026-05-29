package com.caglamurat.smartDisasterHub.enums;

/**
 * Filters for the human moderation pending queue.
 */
public enum ModerationQueueScope {
    /** Posts assigned to the current manager. */
    MINE,
    /** Pending posts with no assignee (claim pool). */
    UNASSIGNED,
    /** All pending posts (admin only). */
    ALL
}
