package ke.ac.strathmore.fmls.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.ac.strathmore.fmls.config.AppProperties;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.User;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stateless authentication tokens (JSON Web Tokens signed with HMAC-SHA256).
 * The token carries the user id and role so the backend can enforce role-based access.
 */
@Component
public class JwtService {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expirySeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtService(AppProperties props) {
        this.secret = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.expirySeconds = props.getJwtExpiryHours() * 3600L;
    }

    public String issue(User user) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> claims = new LinkedHashMap<>();
            long now = Instant.now().getEpochSecond();
            claims.put("sub", String.valueOf(user.getId()));
            claims.put("role", user.getRole().name());
            claims.put("name", user.getName());
            claims.put("iat", now);
            claims.put("exp", now + expirySeconds);
            String h = ENC.encodeToString(mapper.writeValueAsBytes(header));
            String p = ENC.encodeToString(mapper.writeValueAsBytes(claims));
            return h + "." + p + "." + sign(h + "." + p);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create token", e);
        }
    }

    public Optional<CurrentUser> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            String expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            JsonNode claims = mapper.readTree(DEC.decode(parts[1]));
            if (claims.path("exp").asLong(0) < Instant.now().getEpochSecond()) return Optional.empty();
            return Optional.of(new CurrentUser(Long.valueOf(claims.path("sub").asText()),
                    Role.valueOf(claims.path("role").asText()), claims.path("name").asText()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
