package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.RedditAuthor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IRedditAuthorRepository extends JpaRepository<RedditAuthor, Long> {

    Optional<RedditAuthor> findByRedditUsername(String redditUsername);

    Page<RedditAuthor> findByRedditUsernameContainingIgnoreCase(String usernamePart, Pageable pageable);

    @Query("SELECT COALESCE(AVG(a.trustScore), 0) FROM RedditAuthor a WHERE a.trustScore IS NOT NULL")
    Double averageTrustScore();
}
