package com.ecommerce.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    // A valid 256-bit Base64-encoded secret for tests
    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
    }

    @Test
    @DisplayName("generateTokenFromUsername: produces a non-blank token")
    void generateToken_notBlank() {
        String token = jwtUtils.generateTokenFromUsername("alice", 1L);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("validateToken: accepts freshly issued token")
    void validateToken_fresh_true() {
        String token = jwtUtils.generateTokenFromUsername("alice", 1L);
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken: rejects tampered token")
    void validateToken_tampered_false() {
        String token = jwtUtils.generateTokenFromUsername("alice", 1L) + "TAMPERED";
        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("getUsernameFromToken: roundtrips correctly")
    void getUsernameFromToken_roundtrip() {
        String token = jwtUtils.generateTokenFromUsername("bob", 42L);
        assertThat(jwtUtils.getUsernameFromToken(token)).isEqualTo("bob");
    }

    @Test
    @DisplayName("getUserIdFromToken: roundtrips correctly")
    void getUserIdFromToken_roundtrip() {
        String token = jwtUtils.generateTokenFromUsername("bob", 42L);
        assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo(42L);
    }
}
