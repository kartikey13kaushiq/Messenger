package com.myproject.app.messenger.user;

import java.security.Principal;
import java.util.UUID;

/** The authenticated caller, taken from a verified access token. {@link #getName()} is the username. */
public record AuthUser(UUID id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
