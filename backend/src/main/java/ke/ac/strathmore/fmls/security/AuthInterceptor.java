package ke.ac.strathmore.fmls.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.User;
import ke.ac.strathmore.fmls.service.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Access control (Proposal §3.8.8): every API request must carry a valid token, except endpoints
 * marked {@link PublicEndpoint}. The user is re-loaded on every request so that suspending or
 * banning an account takes effect immediately. {@link RequireRole} restricts endpoints by role.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwt;
    private final UserDirectory users;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthInterceptor(JwtService jwt, UserDirectory users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean isPublic = method.hasMethodAnnotation(PublicEndpoint.class);
        Optional<CurrentUser> caller = Optional.empty();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            caller = jwt.verify(header.substring(7).trim());
        }
        if (caller.isEmpty()) {
            if (isPublic) return true;
            return deny(response, 401, "Your session has expired or you are not logged in. Please log in.");
        }
        Optional<User> user = users.find(caller.get().role(), caller.get().id());
        if (user.isEmpty()) {
            return deny(response, 401, "Account no longer exists");
        }
        if (!user.get().canLogIn()) {
            return deny(response, 403, "Your account is " + user.get().getStatus().name().toLowerCase()
                    + ". Contact the administrator.");
        }
        RequireRole rr = method.getMethodAnnotation(RequireRole.class);
        if (rr == null) rr = method.getBeanType().getAnnotation(RequireRole.class);
        final Role callerRole = user.get().getRole();
        if (rr != null && Arrays.stream(rr.value()).noneMatch(r -> r == callerRole)) {
            return deny(response, 403, "You do not have permission to perform this action");
        }
        AuthContext.set(new CurrentUser(user.get().getId(), user.get().getRole(), user.get().getName()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean deny(HttpServletResponse response, int status, String message) throws Exception {
        AuthContext.clear();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", status == 401 ? "Unauthorized" : "Forbidden");
        body.put("message", message);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(body));
        return false;
    }

    /** Convenience for services: is the caller in one of these roles? */
    public static boolean callerIs(Role... roles) {
        return AuthContext.optional().map(u -> Arrays.asList(roles).contains(u.role())).orElse(false);
    }
}
