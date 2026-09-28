package ke.ac.strathmore.fmls.security;

import ke.ac.strathmore.fmls.domain.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Role-based access control: restricts a controller (or method) to the listed roles. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {
    Role[] value();
}
