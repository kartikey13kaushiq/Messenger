package com.myproject.app.messenger.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32)
            @Pattern(regexp = "[A-Za-z0-9_.]+", message = "may contain only letters, digits, '_' and '.'")
            String username,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 80) String displayName) {
    }

    public record UserView(UUID id, String username, String displayName) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
