package ke.ac.strathmore.fmls.security;

import ke.ac.strathmore.fmls.domain.Role;

/** The authenticated caller of the current request (identified from the JWT after sign-in). */
public record CurrentUser(Long id, Role role, String name) {
    public boolean is(Role r) {
        return role == r;
    }
}
