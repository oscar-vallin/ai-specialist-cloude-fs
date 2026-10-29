package com.bookstore.ecommerce.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the member domain (see api/openapi.yaml, tags Auth and Users). */
public final class MemberDtos {

    private MemberDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 8) @Pattern(regexp = "^\\+?[0-9]{1,6}$") String phoneCountryCode,
            @Size(max = 32) @Pattern(regexp = "^[0-9 ()-]{4,32}$") String phoneNumber) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record User(UUID id, String email, String firstName, String lastName, String phoneCountryCode,
            String phoneNumber, String status, List<String> roles) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, User user,
            boolean guestCartMerged) {
    }

    public record AddressRequest(
            @Size(max = 50) String label,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country,
            @Email @Size(max = 255) String email,
            @Size(max = 8) @Pattern(regexp = "^\\+?[0-9]{1,6}$") String phoneCountryCode,
            @Size(max = 32) @Pattern(regexp = "^[0-9 ()-]{4,32}$") String phoneNumber,
            Boolean isDefault) {
    }

    public record Address(UUID id, String label, String firstName, String lastName, String line1, String line2,
            String city, String state, String postalCode, String country, String email, String phoneCountryCode,
            String phoneNumber, boolean isDefault, OffsetDateTime createdAt) {
    }

    public record Wallet(BigDecimal balance, String currency) {
    }

    public record GiftPoints(long balance, int pointsPerCurrencyUnit, BigDecimal currencyValue, String currency) {
    }
}
