package com.bookstore.ecommerce.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberDtos.LoginRequest;
import com.bookstore.ecommerce.member.MemberDtos.LoginResponse;
import com.bookstore.ecommerce.member.MemberDtos.RegisterRequest;
import com.bookstore.ecommerce.member.MemberDtos.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class AuthServiceTest {

    private MemberRepository repository;
    private PasswordEncoder encoder;
    private JwtEncoder jwtEncoder;
    private Clock clock;
    private AppProperties props;
    private ObjectProvider<GuestCartMerger> cartMerger;
    private AuthService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(MemberRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwtEncoder = mock(JwtEncoder.class);
        clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        props = new AppProperties(
                "USD",
                100,
                "Main Store",
                "US",
                new AppProperties.Order(30),
                new AppProperties.Jwt("secret123456789012345678901234567890", 60),
                new AppProperties.Payment("4000000000000002")
        );
        cartMerger = mock(ObjectProvider.class);

        when(encoder.encode(anyString())).thenReturn("$2a$10$hashedpassword");

        service = new AuthService(repository, encoder, jwtEncoder, clock, props, cartMerger);
    }

    @Test
    void registerSucceedsForNewEmail() {
        RegisterRequest req = new RegisterRequest("user@example.com", "Secret123!", "John", "Doe", "+1", "5551234");
        when(repository.emailExists("user@example.com")).thenReturn(false);
        when(repository.insertUser(eq("user@example.com"), anyString(), eq("John"), eq("Doe"), eq("+1"), eq("5551234")))
                .thenReturn(userId);
        when(repository.findUser(userId)).thenReturn(Optional.of(
                new MemberRepository.UserRow(userId, "user@example.com", "$2a$10$hashedpassword", "John", "Doe", "+1",
                        "5551234", "ACTIVE")));
        when(repository.rolesOf(userId)).thenReturn(List.of("CUSTOMER"));

        User user = service.register(req);

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.roles()).containsExactly("CUSTOMER");
        verify(repository).assignRole(userId, "CUSTOMER");
        verify(repository).createAccounts(userId);
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest("existing@example.com", "Secret123!", "John", "Doe", null, null);
        when(repository.emailExists("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        LoginRequest req = new LoginRequest("user@example.com", "Secret123!");
        MemberRepository.UserRow userRow = new MemberRepository.UserRow(userId, "user@example.com", "$2a$10$hashedpassword",
                "John", "Doe", null, null, "ACTIVE");

        when(repository.findUserByEmail("user@example.com")).thenReturn(Optional.of(userRow));
        when(encoder.matches("Secret123!", "$2a$10$hashedpassword")).thenReturn(true);
        when(repository.rolesOf(userId)).thenReturn(List.of("CUSTOMER"));

        org.springframework.security.oauth2.jwt.Jwt mockJwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("jwt-token-string");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        LoginResponse response = service.login(req, null);

        assertThat(response.accessToken()).isEqualTo("jwt-token-string");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(userId);
        assertThat(response.guestCartMerged()).isFalse();
    }

    @Test
    void loginThrowsOnWrongPassword() {
        LoginRequest req = new LoginRequest("user@example.com", "WrongPassword");
        MemberRepository.UserRow userRow = new MemberRepository.UserRow(userId, "user@example.com", "$2a$10$hashedpassword",
                "John", "Doe", null, null, "ACTIVE");

        when(repository.findUserByEmail("user@example.com")).thenReturn(Optional.of(userRow));
        when(encoder.matches("WrongPassword", "$2a$10$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> service.login(req, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void loginThrowsOnUnknownEmail() {
        LoginRequest req = new LoginRequest("unknown@example.com", "Secret123!");
        when(repository.findUserByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(encoder.matches(eq("Secret123!"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login(req, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void loginThrowsWhenAccountDisabled() {
        LoginRequest req = new LoginRequest("disabled@example.com", "Secret123!");
        MemberRepository.UserRow userRow = new MemberRepository.UserRow(userId, "disabled@example.com", "$2a$10$hashedpassword",
                "John", "Doe", null, null, "DISABLED");

        when(repository.findUserByEmail("disabled@example.com")).thenReturn(Optional.of(userRow));
        when(encoder.matches("Secret123!", "$2a$10$hashedpassword")).thenReturn(true);

        assertThatThrownBy(() -> service.login(req, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.ACCOUNT_DISABLED));
    }

    @Test
    void loginMergesGuestCartWhenProvided() {
        LoginRequest req = new LoginRequest("user@example.com", "Secret123!");
        MemberRepository.UserRow userRow = new MemberRepository.UserRow(userId, "user@example.com", "$2a$10$hashedpassword",
                "John", "Doe", null, null, "ACTIVE");

        GuestCartMerger merger = mock(GuestCartMerger.class);
        when(cartMerger.getIfAvailable()).thenReturn(merger);
        when(merger.merge(userId, "guest-tok-123")).thenReturn(true);

        when(repository.findUserByEmail("user@example.com")).thenReturn(Optional.of(userRow));
        when(encoder.matches("Secret123!", "$2a$10$hashedpassword")).thenReturn(true);
        when(repository.rolesOf(userId)).thenReturn(List.of("CUSTOMER"));

        org.springframework.security.oauth2.jwt.Jwt mockJwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("jwt-token-string");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        LoginResponse response = service.login(req, "guest-tok-123");

        assertThat(response.guestCartMerged()).isTrue();
        verify(merger).merge(userId, "guest-tok-123");
    }

    @Test
    void logoutRevokesToken() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn("jti-123");
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(jwt.getExpiresAt()).thenReturn(Instant.parse("2026-01-01T13:00:00Z"));

        service.logout(jwt);

        verify(repository).revokeToken(eq("jti-123"), eq(userId), any());
        verify(repository).deleteExpiredRevocations(any());
    }

    @Test
    void currentUserReturnsUserWhenFound() {
        MemberRepository.UserRow userRow = new MemberRepository.UserRow(userId, "user@example.com", "$2a$10$hashedpassword",
                "John", "Doe", null, null, "ACTIVE");
        when(repository.findUser(userId)).thenReturn(Optional.of(userRow));
        when(repository.rolesOf(userId)).thenReturn(List.of("CUSTOMER"));

        User user = service.currentUser(userId);

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("user@example.com");
    }

    @Test
    void currentUserThrowsWhenMissing() {
        when(repository.findUser(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentUser(userId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
