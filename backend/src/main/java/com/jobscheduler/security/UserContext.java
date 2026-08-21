package com.jobscheduler.security;

import com.jobscheduler.entity.User;

/**
 * Thread-local holder for the authenticated user of the current request.
 * Set by JwtAuthenticationFilter; consumed by services and controllers.
 */
public final class UserContext {

    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(User user) {
        CURRENT_USER.set(user);
    }

    public static User get() {
        User user = CURRENT_USER.get();
        if (user == null) {
            throw new IllegalStateException("No authenticated user in context. " +
                    "Ensure request passed through JwtAuthenticationFilter.");
        }
        return user;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
