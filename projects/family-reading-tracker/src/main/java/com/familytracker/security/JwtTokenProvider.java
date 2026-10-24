package com.familytracker.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local prototype tokens. Without an environment secret, restart invalidates sessions. */
@Component
public class JwtTokenProvider {

    private final Key key;

    public JwtTokenProvider(@Value("${app.jwt.secret:}") String secret) {
        key = secret.isBlank()
            ? Keys.secretKeyFor(SignatureAlgorithm.HS256)
            : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(3600)))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    public String username(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }
}
