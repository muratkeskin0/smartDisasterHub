-- Moderation queue assignment (claim workflow)
ALTER TABLE reddit_posts
    ADD COLUMN IF NOT EXISTS assigned_moderator_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP NULL;

ALTER TABLE reddit_posts
    ADD CONSTRAINT fk_reddit_posts_assigned_moderator
        FOREIGN KEY (assigned_moderator_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_reddit_posts_assigned_moderator
    ON reddit_posts(assigned_moderator_id);
