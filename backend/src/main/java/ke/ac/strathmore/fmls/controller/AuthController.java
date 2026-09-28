package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.dto.AuthResponse;
import ke.ac.strathmore.fmls.dto.LoginRequest;
import ke.ac.strathmore.fmls.dto.RegisterRequest;
import ke.ac.strathmore.fmls.dto.UpdateProfileRequest;
import ke.ac.strathmore.fmls.dto.UserView;
import ke.ac.strathmore.fmls.security.PublicEndpoint;
import ke.ac.strathmore.fmls.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User Management Module – registration, login, profile. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PublicEndpoint
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PublicEndpoint
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/me")
    public UserView me() {
        return auth.me();
    }

    @PutMapping("/me")
    public UserView update(@Valid @RequestBody UpdateProfileRequest req) {
        return auth.updateProfile(req);
    }
}
