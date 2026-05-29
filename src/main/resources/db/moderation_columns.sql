-- Run once if Hibernate ddl-auto does not add columns (MySQL).
ALTER TABLE reddit_posts
    ADD COLUMN moderation_status VARCHAR(20) NULL,
    ADD COLUMN moderation_reviewed_at DATETIME(6) NULL,
    ADD COLUMN moderation_reviewed_by VARCHAR(150) NULL,
    ADD COLUMN moderation_notes TEXT NULL;

ALTER TABLE reddit_authors
    ADD COLUMN moderation_approved_posts INT NOT NULL DEFAULT 0,
    ADD COLUMN moderation_rejected_posts INT NOT NULL DEFAULT 0;

CREATE INDEX idx_moderation_status ON reddit_posts (moderation_status);

-- Legacy analyzed rows (optional):
-- UPDATE reddit_posts SET moderation_status = 'APPROVED', is_disaster_related = 0
--   WHERE status = 'ANALYZED' AND moderation_status IS NULL AND (is_disaster_related = 0 OR is_disaster_related IS NULL);
-- UPDATE reddit_posts SET moderation_status = 'PENDING_REVIEW'
--   WHERE status = 'ANALYZED' AND moderation_status IS NULL AND is_disaster_related = 1;
