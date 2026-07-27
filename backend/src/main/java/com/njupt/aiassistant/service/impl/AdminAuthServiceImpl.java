package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AdminSecurityProperties;
import com.njupt.aiassistant.config.JwtProperties;
import com.njupt.aiassistant.dto.AdminLoginRequest;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.AdminAuthService;
import com.njupt.aiassistant.vo.AdminTokenVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminSecurityProperties adminProperties;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AdminAuthServiceImpl(
            AdminSecurityProperties adminProperties,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder
    ) {
        this.adminProperties = adminProperties;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public AdminTokenVO login(AdminLoginRequest request) {
        if (adminProperties.username().isBlank()
                || adminProperties.passwordHash().isBlank()) {
            throw new BusinessException(
                    ErrorCode.ADMIN_CONFIGURATION_INVALID
            );
        }
        var usernameMatches = adminProperties.username()
                .equals(request.username().trim());
        var passwordMatches = passwordEncoder.matches(
                request.password(),
                adminProperties.passwordHash()
        );
        if (!usernameMatches || !passwordMatches) {
            throw new BusinessException(
                    ErrorCode.ADMIN_CREDENTIALS_INVALID
            );
        }

        var issuedAt = Instant.now();
        var expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer("njupt-ai-assistant")
                .subject(adminProperties.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("admin_id", adminProperties.id())
                .claim("role", "ADMIN")
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new AdminTokenVO(
                token,
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                adminProperties.username()
        );
    }
}
