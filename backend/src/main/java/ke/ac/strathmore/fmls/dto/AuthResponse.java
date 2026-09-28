package ke.ac.strathmore.fmls.dto;


/** Token + profile returned after login/registration. */
public record AuthResponse(
        String token,
        UserView user) {
}
