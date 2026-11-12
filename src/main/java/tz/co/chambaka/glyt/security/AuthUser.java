package tz.co.chambaka.glyt.security;

import tz.co.chambaka.glyt.domain.enums.Role;

public record AuthUser(
        Long id,
        String email,
        String fullName,
        Role role,
        String location,
        int tokenVersion,
        boolean mustChangePassword
) {}
