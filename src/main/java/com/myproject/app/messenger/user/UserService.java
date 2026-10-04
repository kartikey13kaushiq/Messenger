package com.myproject.app.messenger.user;

import com.myproject.app.messenger.config.JwtService;
import com.myproject.app.messenger.error.ApiException;
import com.myproject.app.messenger.user.UserDtos.LoginRequest;
import com.myproject.app.messenger.user.UserDtos.RegisterRequest;
import com.myproject.app.messenger.user.UserDtos.TokenResponse;
import com.myproject.app.messenger.user.UserDtos.UserView;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    /** Compared against when the username is unknown, so both failure paths cost one BCrypt check. */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }
        try {
            User user = users.saveAndFlush(new User(username, request.displayName().trim(),
                    passwordEncoder.encode(request.password()), clock.instant()));
            return UserView.of(user);
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var user = users.findByUsernameIgnoreCase(request.username().trim());
        boolean ok = passwordEncoder.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !ok) {
            throw ApiException.unauthorized("Invalid username or password");
        }
        User u = user.get();
        return new TokenResponse(jwtService.issue(u.getId(), u.getUsername()), "Bearer", jwtService.ttlSeconds(),
                UserView.of(u));
    }

    @Transactional(readOnly = true)
    public UserView me(UUID userId) {
        return UserView.of(require(userId));
    }

    @Transactional
    public UserView updateProfile(UUID userId, String displayName) {
        User user = require(userId);
        user.setDisplayName(displayName.trim());
        return UserView.of(user);
    }

    @Transactional(readOnly = true)
    public List<UserView> search(UUID userId, String query) {
        // Strip LIKE wildcards so a query cannot match everyone; what remains is matched literally.
        String q = query == null ? "" : query.replace("%", "").replace("_", "").replace("\\", "").trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return users.search(q, userId, PageRequest.of(0, 20)).stream().map(UserView::of).toList();
    }

    User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
