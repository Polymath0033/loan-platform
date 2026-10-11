package com.loan.apigateway.auth.repository;


import com.loan.apigateway.auth.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {
    @Query("SELECT u FROM Users u WHERE u.email = :email AND u.isActive = true")
    Optional<Users> findByEmail(String email);
    Optional<Users> findById(UUID id);
}

