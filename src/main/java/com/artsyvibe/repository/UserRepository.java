package com.artsyvibe.repository;

import com.artsyvibe.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByNameIgnoreCase(String name);

    List<User> findByRoleIgnoreCase(String role);

    /** Everyone except the given role (used for broadcast notifications). */
    List<User> findByRoleNotIgnoreCase(String role);

    @Query("SELECT u.status FROM User u WHERE u.userId = :id")
    Optional<String> findStatusByUserId(@Param("id") Long id);

    /** Users whose password column is not a BCrypt hash (legacy plain-text rows). */
    @Query("SELECT u FROM User u WHERE u.password IS NOT NULL AND u.password NOT LIKE '$2%'")
    List<User> findWithLegacyPasswords();

    long countByJoinDateBetween(LocalDateTime from, LocalDateTime to);

    List<User> findByJoinDateBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Admin "Users" page: customers and artisans (not admins, not closed accounts), filtered and paged in SQL.
     * Empty-string parameters mean "no filter" (avoids untyped NULL parameters on PostgreSQL).
     * {@code q} is a LIKE pattern from PageView.likePattern.
     */
    @Query("SELECT u FROM User u WHERE UPPER(u.role) <> 'ADMIN' AND (u.status IS NULL OR LOWER(u.status) <> 'deleted') "
            + "AND (:q = '' OR LOWER(u.name) LIKE :q ESCAPE '\\' OR LOWER(u.email) LIKE :q ESCAPE '\\') "
            + "AND (:role = '' OR UPPER(u.role) = UPPER(:role)) "
            + "AND (:status = '' OR LOWER(u.status) = LOWER(:status) "
            + "     OR (LOWER(:status) = 'pending' AND LOWER(u.status) = 'pending approval'))")
    Page<User> searchForAdmin(@Param("q") String q, @Param("role") String role, @Param("status") String status,
            Pageable pageable);
}
