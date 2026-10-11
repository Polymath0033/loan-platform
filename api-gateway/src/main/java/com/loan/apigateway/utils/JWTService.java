package com.loan.apigateway.utils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * @author Yusuf Olosan
 * @role software engineer
 * @createdOn 11 Sun Oct, 2026
 */

@Component
public class JWTService {
    @Value("${jwt.secret}")
    private String secret;

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return extractClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return extractClaims(token).getExpiration();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token) {
        // Fix #3: validate both that a subject exists and that the token has not expired.
        String subject = extractSubject(token);
        return subject != null && !subject.isBlank() && !isTokenExpired(token);
    }



    private SecretKey getSecretKey() {
        byte[] decodedKey = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(decodedKey);
    }
}
