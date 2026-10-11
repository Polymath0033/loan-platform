package com.loan.apigateway.auth.repository;


import com.loan.apigateway.auth.model.Token;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    @Query("SELECT t FROM Token t JOIN FETCH Users u ON t.user.id = u.id WHERE t.user.id = :userId AND t.isLoggedOut = false")
    List<Token> findAllActiveTokensByUserId(@Param("userId") UUID userId);
    Optional<Token> findByAccessToken(String accessToken);
    Optional<Token> findByRefreshToken(String refreshToken);
}
