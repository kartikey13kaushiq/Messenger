package com.myproject.app.messenger.user;

import com.myproject.app.messenger.user.UserDtos.UpdateProfileRequest;
import com.myproject.app.messenger.user.UserDtos.UserView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserView me(@AuthenticationPrincipal AuthUser me) {
        return userService.me(me.id());
    }

    @PatchMapping("/me")
    UserView update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(me.id(), request.displayName());
    }

    /** People to start a conversation with: username prefix or display-name substring, excluding the caller. */
    @GetMapping
    List<UserView> search(@AuthenticationPrincipal AuthUser me, @RequestParam("q") String query) {
        return userService.search(me.id(), query);
    }
}
