package ke.ac.strathmore.fmls.security;

import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.service.ApiException;

import java.util.Optional;

/** Holds the authenticated user for the duration of a request (set by {@link AuthInterceptor}). */
public final class AuthContext {
    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private AuthContext() { }

    static void set(CurrentUser user) { CURRENT.set(user); }

    static void clear() { CURRENT.remove(); }

    public static Optional<CurrentUser> optional() { return Optional.ofNullable(CURRENT.get()); }

    public static CurrentUser get() {
        CurrentUser u = CURRENT.get();
        if (u == null) throw ApiException.unauthorized("Please log in");
        return u;
    }

    public static CurrentUser require(Role role) {
        CurrentUser u = get();
        if (u.role() != role) throw ApiException.forbidden("This action is only available to " + role.name().toLowerCase() + "s");
        return u;
    }
}
