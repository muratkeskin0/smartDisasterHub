-- User complaints submitted by BASIC users; reviewed in staff inbox
CREATE TABLE IF NOT EXISTS user_complaints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submitter_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    assigned_staff_id BIGINT NULL,
    assigned_at TIMESTAMP NULL,
    staff_notes TEXT NULL,
    resolved_at TIMESTAMP NULL,
    resolved_by VARCHAR(150) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_user_complaints_submitter FOREIGN KEY (submitter_id) REFERENCES users(id),
    CONSTRAINT fk_user_complaints_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES users(id)
);

CREATE INDEX idx_user_complaints_status ON user_complaints(status);
CREATE INDEX idx_user_complaints_submitter ON user_complaints(submitter_id);
CREATE INDEX idx_user_complaints_assigned_staff ON user_complaints(assigned_staff_id);
