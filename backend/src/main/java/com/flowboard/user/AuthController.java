package com.flowboard.user;

import com.flowboard.security.CurrentUser;
import com.flowboard.user.UserDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Auth & profile")
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return users.register(req);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return users.login(req);
    }

    @GetMapping("/users/me")
    public UserSummary me() {
        return UserSummary.of(users.get(CurrentUser.id()));
    }

    @PatchMapping("/users/me")
    public UserSummary update(@Valid @RequestBody UpdateProfileRequest req) {
        return users.updateProfile(CurrentUser.id(), req);
    }
}
