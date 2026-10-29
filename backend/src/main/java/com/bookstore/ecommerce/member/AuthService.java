package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberDtos.LoginRequest;
import com.bookstore.ecommerce.member.MemberDtos.LoginResponse;
import com.bookstore.ecommerce.member.MemberDtos.RegisterRequest;
import com.bookstore.ecommerce.member.MemberDtos.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository repository;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final AppProperties props;
    private final ObjectProvider<GuestCartMerger> cartMerger;
    /** Used to spend the same BCrypt time when the e-mail is unknown, so response time does not reveal accounts. */
    private final String dummyHash;

    AuthService(MemberRepository repository, PasswordEncoder encoder, JwtEncoder jwtEncoder, Clock clock,
            AppProperties props, ObjectProvider<GuestCartMerger> cartMerger) {
        this.repository = repository;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.props = props;
        this.cartMerger = cartMerger;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim();
        if (repository.emailExists(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED, "This e-mail address is already registered.");
        }
        UUID id;
        try {
            id = repository.insertUser(email, encoder.encode(request.password()), request.firstName().trim(),
                    request.lastName().trim(), request.phoneCountryCode(), request.phoneNumber());
        } catch (DuplicateKeyException e) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_REGISTERED, "This e-mail address is already registered.");
        }
        repository.assignRole(id, "CUSTOMER");
        repository.createAccounts(id);
        log.info("User registered: {}", id);
        return toUser(repository.findUser(id).orElseThrow());
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String guestToken) {
        var user = repository.findUserByEmail(request.email().trim()).orElse(null);
        String hash = user == null ? dummyHash : user.passwordHash();
        boolean matches = encoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "The e-mail or the password is incorrect.");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw ApiException.of(ErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }
        List<String> roles = repository.rolesOf(user.id());
        boolean merged = guestToken != null && !guestToken.isBlank()
                && cartMerger.getIfAvailable() != null && cartMerger.getIfAvailable().merge(user.id(), guestToken);

        Instant now = clock.instant();
        Duration ttl = Duration.ofMinutes(props.jwt().expirationMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", roles)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        log.info("User logged in: {}", user.id());
        return new LoginResponse(token, "Bearer", ttl.toSeconds(), toUser(user, roles), merged);
    }

    /** Puts the token on the deny-list until it would have expired anyway. */
    @Transactional
    public void logout(Jwt token) {
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(token.getExpiresAt(), ZoneOffset.UTC);
        repository.revokeToken(token.getId(), UUID.fromString(token.getSubject()), expiresAt);
        repository.deleteExpiredRevocations(OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return toUser(repository.findUser(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHENTICATED, "The account no longer exists.")));
    }

    private User toUser(MemberRepository.UserRow row) {
        return toUser(row, repository.rolesOf(row.id()));
    }

    private static User toUser(MemberRepository.UserRow row, List<String> roles) {
        return new User(row.id(), row.email(), row.firstName(), row.lastName(), row.phoneCountryCode(),
                row.phoneNumber(), row.status(), roles);
    }
}
