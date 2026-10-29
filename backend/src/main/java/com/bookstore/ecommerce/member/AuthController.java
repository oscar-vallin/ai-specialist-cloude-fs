package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.member.MemberDtos.LoginRequest;
import com.bookstore.ecommerce.member.MemberDtos.LoginResponse;
import com.bookstore.ecommerce.member.MemberDtos.RegisterRequest;
import com.bookstore.ecommerce.member.MemberDtos.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", "/api/v1/users/me")
                .body(service.register(request));
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken) {
        return service.login(request, guestToken);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt token) {
        service.logout(token);
    }
}
