package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.member.MemberDtos.AddressRequest;
import com.bookstore.ecommerce.member.MemberDtos.GiftPoints;
import com.bookstore.ecommerce.member.MemberDtos.User;
import com.bookstore.ecommerce.member.MemberDtos.Wallet;
import com.bookstore.ecommerce.store.StoreService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
class UserController {

    private final AuthService authService;
    private final AddressService addresses;
    private final WalletService wallets;
    private final GiftPointService points;
    private final StoreService stores;
    private final AppProperties props;

    UserController(AuthService authService, AddressService addresses, WalletService wallets, GiftPointService points,
            StoreService stores, AppProperties props) {
        this.authService = authService;
        this.addresses = addresses;
        this.wallets = wallets;
        this.points = points;
        this.stores = stores;
        this.props = props;
    }

    @GetMapping
    User me(Caller caller) {
        return authService.currentUser(caller.requireUser());
    }

    @GetMapping("/addresses")
    List<Address> list(Caller caller) {
        return addresses.list(caller.requireUser());
    }

    @PostMapping("/addresses")
    ResponseEntity<Address> create(Caller caller, @Valid @RequestBody AddressRequest request) {
        Address created = addresses.create(caller.requireUser(), request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + created.id())).body(created);
    }

    @GetMapping("/addresses/{addressId}")
    Address get(Caller caller, @PathVariable UUID addressId) {
        return addresses.get(caller.requireUser(), addressId);
    }

    @PutMapping("/addresses/{addressId}")
    Address update(Caller caller, @PathVariable UUID addressId, @Valid @RequestBody AddressRequest request) {
        return addresses.update(caller.requireUser(), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Caller caller, @PathVariable UUID addressId) {
        addresses.delete(caller.requireUser(), addressId);
    }

    @PostMapping("/addresses/{addressId}/default")
    Address setDefault(Caller caller, @PathVariable UUID addressId) {
        return addresses.setDefault(caller.requireUser(), addressId);
    }

    @GetMapping("/wallet")
    Wallet wallet(Caller caller) {
        BigDecimal balance = wallets.balance(caller.requireUser());
        return new Wallet(balance, props.currency());
    }

    @GetMapping("/gift-points")
    GiftPoints giftPoints(Caller caller) {
        long balance = points.pointsBalance(caller.requireUser());
        int rate = stores.defaultStorePolicy().giftPointsPerCurrencyUnit();
        BigDecimal value = BigDecimal.valueOf(balance).divide(BigDecimal.valueOf(rate), 2, java.math.RoundingMode.DOWN);
        return new GiftPoints(balance, rate, value, props.currency());
    }
}
