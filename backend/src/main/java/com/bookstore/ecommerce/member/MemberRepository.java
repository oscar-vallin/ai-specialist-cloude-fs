package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.member.MemberDtos.AddressRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** All SQL of the member domain. Only the member services use it. */
@Repository
class MemberRepository {

    record UserRow(UUID id, String email, String passwordHash, String firstName, String lastName,
            String phoneCountryCode, String phoneNumber, String status) {
    }

    private static final RowMapper<UserRow> USER = (rs, n) -> new UserRow(rs.getObject("id", UUID.class),
            rs.getString("email"), rs.getString("password_hash"), rs.getString("first_name"),
            rs.getString("last_name"), rs.getString("phone_country_code"), rs.getString("phone_number"),
            rs.getString("status"));

    private static final RowMapper<Address> ADDRESS = (rs, n) -> new Address(rs.getObject("id", UUID.class),
            rs.getString("label"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("line1"),
            rs.getString("line2"), rs.getString("city"), rs.getString("state"), rs.getString("postal_code"),
            rs.getString("country"), rs.getString("email"), rs.getString("phone_country_code"),
            rs.getString("phone_number"), rs.getBoolean("is_default"),
            rs.getObject("created_at", OffsetDateTime.class));

    private static final String ADDRESS_COLUMNS = "id, label, first_name, last_name, line1, line2, city, state, "
            + "postal_code, country, email, phone_country_code, phone_number, is_default, created_at";

    private final JdbcClient jdbc;

    MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean emailExists(String email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower(:email))")
                .param("email", email).query(Boolean.class).single();
    }

    UUID insertUser(String email, String passwordHash, String firstName, String lastName, String phoneCountryCode,
            String phoneNumber) {
        return jdbc.sql("""
                INSERT INTO users (email, password_hash, first_name, last_name, phone_country_code, phone_number)
                VALUES (:email, :hash, :first, :last, :cc, :phone) RETURNING id""")
                .param("email", email).param("hash", passwordHash).param("first", firstName)
                .param("last", lastName).param("cc", phoneCountryCode).param("phone", phoneNumber)
                .query(UUID.class).single();
    }

    void assignRole(UUID userId, String role) {
        jdbc.sql("INSERT INTO user_roles (user_id, role_id) SELECT :user, id FROM roles WHERE name = :role")
                .param("user", userId).param("role", role).update();
    }

    void createAccounts(UUID userId) {
        jdbc.sql("INSERT INTO wallets (user_id) VALUES (:user)").param("user", userId).update();
        jdbc.sql("INSERT INTO gift_point_accounts (user_id) VALUES (:user)").param("user", userId).update();
    }

    Optional<UserRow> findUserByEmail(String email) {
        return jdbc.sql("SELECT * FROM users WHERE lower(email) = lower(:email)")
                .param("email", email).query(USER).optional();
    }

    Optional<UserRow> findUser(UUID id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id").param("id", id).query(USER).optional();
    }

    List<String> rolesOf(UUID userId) {
        return jdbc.sql("""
                SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = :user ORDER BY r.name""").param("user", userId).query(String.class).list();
    }

    void revokeToken(String jti, UUID userId, OffsetDateTime expiresAt) {
        jdbc.sql("""
                INSERT INTO revoked_tokens (jti, user_id, expires_at) VALUES (:jti, :user, :exp)
                ON CONFLICT (jti) DO NOTHING""").param("jti", jti).param("user", userId).param("exp", expiresAt).update();
    }

    int deleteExpiredRevocations(OffsetDateTime now) {
        return jdbc.sql("DELETE FROM revoked_tokens WHERE expires_at < :now").param("now", now).update();
    }

    // ----- addresses -----

    List<Address> listAddresses(UUID userId) {
        return jdbc.sql("SELECT " + ADDRESS_COLUMNS
                + " FROM addresses WHERE user_id = :user ORDER BY is_default DESC, created_at, id")
                .param("user", userId).query(ADDRESS).list();
    }

    Optional<Address> findAddress(UUID userId, UUID addressId) {
        return jdbc.sql("SELECT " + ADDRESS_COLUMNS + " FROM addresses WHERE id = :id AND user_id = :user")
                .param("id", addressId).param("user", userId).query(ADDRESS).optional();
    }

    long countAddresses(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM addresses WHERE user_id = :user").param("user", userId)
                .query(Long.class).single();
    }

    void clearDefault(UUID userId) {
        jdbc.sql("UPDATE addresses SET is_default = FALSE WHERE user_id = :user AND is_default")
                .param("user", userId).update();
    }

    UUID insertAddress(UUID userId, AddressRequest a, boolean isDefault) {
        return jdbc.sql("""
                INSERT INTO addresses (user_id, label, first_name, last_name, line1, line2, city, state, postal_code,
                    country, email, phone_country_code, phone_number, is_default)
                VALUES (:user, :label, :first, :last, :line1, :line2, :city, :state, :postal, :country, :email,
                    :cc, :phone, :def) RETURNING id""")
                .param("user", userId).param("label", a.label()).param("first", a.firstName())
                .param("last", a.lastName()).param("line1", a.line1()).param("line2", a.line2())
                .param("city", a.city()).param("state", a.state()).param("postal", a.postalCode())
                .param("country", a.country()).param("email", a.email()).param("cc", a.phoneCountryCode())
                .param("phone", a.phoneNumber()).param("def", isDefault).query(UUID.class).single();
    }

    void updateAddress(UUID addressId, AddressRequest a, boolean isDefault) {
        jdbc.sql("""
                UPDATE addresses SET label = :label, first_name = :first, last_name = :last, line1 = :line1,
                    line2 = :line2, city = :city, state = :state, postal_code = :postal, country = :country,
                    email = :email, phone_country_code = :cc, phone_number = :phone, is_default = :def
                WHERE id = :id""")
                .param("id", addressId).param("label", a.label()).param("first", a.firstName())
                .param("last", a.lastName()).param("line1", a.line1()).param("line2", a.line2())
                .param("city", a.city()).param("state", a.state()).param("postal", a.postalCode())
                .param("country", a.country()).param("email", a.email()).param("cc", a.phoneCountryCode())
                .param("phone", a.phoneNumber()).param("def", isDefault).update();
    }

    void setDefault(UUID addressId) {
        jdbc.sql("UPDATE addresses SET is_default = TRUE WHERE id = :id").param("id", addressId).update();
    }

    void deleteAddress(UUID addressId) {
        jdbc.sql("DELETE FROM addresses WHERE id = :id").param("id", addressId).update();
    }

    Optional<String> defaultAddressCountry(UUID userId) {
        return jdbc.sql("SELECT country FROM addresses WHERE user_id = :user AND is_default")
                .param("user", userId).query(String.class).optional();
    }

    // ----- wallet and gift points -----

    record WalletRow(UUID id, BigDecimal balance) {
    }

    Optional<WalletRow> findWallet(UUID userId) {
        return jdbc.sql("SELECT id, balance FROM wallets WHERE user_id = :user").param("user", userId)
                .query((rs, n) -> new WalletRow(rs.getObject("id", UUID.class), rs.getBigDecimal("balance"))).optional();
    }

    /** Locks the wallet row for the rest of the transaction (serializes concurrent debits). */
    Optional<WalletRow> lockWallet(UUID userId) {
        return jdbc.sql("SELECT id, balance FROM wallets WHERE user_id = :user FOR UPDATE").param("user", userId)
                .query((rs, n) -> new WalletRow(rs.getObject("id", UUID.class), rs.getBigDecimal("balance"))).optional();
    }

    void setWalletBalance(UUID walletId, BigDecimal balance) {
        jdbc.sql("UPDATE wallets SET balance = :balance, version = version + 1 WHERE id = :id")
                .param("balance", balance).param("id", walletId).update();
    }

    void insertWalletTransaction(UUID walletId, String type, BigDecimal amount, String reason, UUID orderId,
            UUID paymentId, UUID refundId, BigDecimal balanceAfter) {
        jdbc.sql("""
                INSERT INTO wallet_transactions (wallet_id, type, amount, reason, order_id, payment_id, refund_id,
                    balance_after) VALUES (:wallet, :type, :amount, :reason, :order, :payment, :refund, :after)""")
                .param("wallet", walletId).param("type", type).param("amount", amount).param("reason", reason)
                .param("order", orderId).param("payment", paymentId).param("refund", refundId)
                .param("after", balanceAfter).update();
    }

    Optional<Long> lockGiftPointBalance(UUID userId) {
        return jdbc.sql("SELECT balance FROM gift_point_accounts WHERE user_id = :user FOR UPDATE")
                .param("user", userId).query(Long.class).optional();
    }

    Optional<Long> findGiftPointBalance(UUID userId) {
        return jdbc.sql("SELECT balance FROM gift_point_accounts WHERE user_id = :user")
                .param("user", userId).query(Long.class).optional();
    }

    void setGiftPointBalance(UUID userId, long balance) {
        jdbc.sql("UPDATE gift_point_accounts SET balance = :balance, version = version + 1 WHERE user_id = :user")
                .param("balance", balance).param("user", userId).update();
    }

    void insertGiftPointLedger(UUID userId, long points, String type, UUID orderId, UUID paymentId, UUID refundId,
            long balanceAfter) {
        jdbc.sql("""
                INSERT INTO gift_point_ledger (user_id, points, type, order_id, payment_id, refund_id, balance_after)
                VALUES (:user, :points, :type, :order, :payment, :refund, :after)""")
                .param("user", userId).param("points", points).param("type", type).param("order", orderId)
                .param("payment", paymentId).param("refund", refundId).param("after", balanceAfter).update();
    }
}
