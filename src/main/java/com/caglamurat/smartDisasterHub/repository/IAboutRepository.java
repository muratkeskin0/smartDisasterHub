package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.About;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IAboutRepository extends JpaRepository<About, Long> {
    
    Optional<About> findFirstByOrderByIdAsc();
}





