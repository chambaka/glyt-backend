package tz.co.chambaka.glyt.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.AuthService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ChangePasswordRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ForgotRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.RefreshRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ResetRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public MessageResponse logout(@RequestBody(required = false) RefreshRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
        return new MessageResponse("Signed out.", null);
    }

    @PostMapping("/logout-all")
    public MessageResponse logoutAll() {
        auth.logoutAll();
        return new MessageResponse("Signed out on every device.", null);
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(@RequestBody ChangePasswordRequest request) {
        return auth.changePassword(request.currentPassword(), request.newPassword());
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgot(@RequestBody ForgotRequest request) {
        return auth.forgot(request.email());
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@RequestBody ResetRequest request) {
        return auth.reset(request.token(), request.password());
    }

    @GetMapping("/me")
    public UserView me() {
        return auth.me();
    }
}
