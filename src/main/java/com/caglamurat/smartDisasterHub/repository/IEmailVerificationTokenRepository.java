package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.EmailVerificationToken;
import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.enums.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IEmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteByUserAndPurpose(User user, EmailVerificationPurpose purpose);

    Optional<EmailVerificationToken> findFirstByUserAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            User user, EmailVerificationPurpose purpose, Instant now);

    void deleteByExpiresAtBefore(Instant cutoff);
}
