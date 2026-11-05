package com.example.demo.repository;

import com.example.demo.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserEmail(String userEmail);

    /** Addresses are keyed by e-mail, so they must follow the user when the e-mail changes. */
    @Modifying
    @Query("UPDATE Address a SET a.userEmail = :newEmail WHERE a.userEmail = :oldEmail")
    int reassignEmail(@Param("oldEmail") String oldEmail, @Param("newEmail") String newEmail);
}
