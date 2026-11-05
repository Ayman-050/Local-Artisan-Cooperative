package com.example.demo.repository;

import com.example.demo.model.Artisan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtisanRepository extends JpaRepository<Artisan, Long> {
    Optional<Artisan> findByEmail(String email);
    List<Artisan> findByStatus(String status);
    List<Artisan> findByStatusIn(List<String> statuses);
}

