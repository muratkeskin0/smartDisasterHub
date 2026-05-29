package com.caglamurat.smartDisasterHub.enums;

/**
 * Human-in-the-loop moderation state (separate from ML pipeline {@link RedditPostStatus}).
 */
public enum PostModerationStatus {
    /** Afet adayı; harita/raporlarda görünmez, moderasyon kuyruğunda. */
    PENDING_REVIEW,
    /** İnsan onayı; harita/raporlarda (afet ile ilgili ise) kullanılır. */
    APPROVED,
    /** Reddedildi; {@code isDisasterRelated} false, yazar güveni düşer. */
    REJECTED,
    /**
     * Afet adayı değil; moderasyon kuyruğuna alınmaz. Haritada görünmez.
     * {@link #APPROVED} değildir — "düşük skor otomatik onay" anlamına gelmez.
     */
    NOT_REQUIRED
}
