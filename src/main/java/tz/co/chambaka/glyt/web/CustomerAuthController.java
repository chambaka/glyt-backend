package tz.co.chambaka.glyt.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.CustomerAuthService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerAuthResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerProfileView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRegisterRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.RefreshRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/customer/auth")
public class CustomerAuthController {

    private final CustomerAuthService auth;

    public CustomerAuthController(CustomerAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public CustomerAuthResponse register(@RequestBody CustomerRegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public CustomerAuthResponse login(@RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public CustomerAuthResponse refresh(@RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody(required = false) RefreshRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
        return Map.of("message", "Signed out.");
    }

    @GetMapping("/me")
    public CustomerProfileView me() {
        return auth.me();
    }
}
