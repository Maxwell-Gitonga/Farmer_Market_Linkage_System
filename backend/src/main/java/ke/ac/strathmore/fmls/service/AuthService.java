package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.config.AppProperties;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.User;
import ke.ac.strathmore.fmls.dto.AuthResponse;
import ke.ac.strathmore.fmls.dto.LoginRequest;
import ke.ac.strathmore.fmls.dto.RegisterRequest;
import ke.ac.strathmore.fmls.dto.UpdateProfileRequest;
import ke.ac.strathmore.fmls.dto.UserView;
import ke.ac.strathmore.fmls.module.CropCatalog;
import ke.ac.strathmore.fmls.repository.AdminRepository;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.FarmerRepository;
import ke.ac.strathmore.fmls.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User Management Module (Proposal §3.8.1): registration, login, authentication.
 * One sign-in for everybody – after login the system identifies whether the user is a farmer,
 * buyer or administrator from the stored data and grants the matching functionality.
 */
@Service
public class AuthService {

    private final FarmerRepository farmers;
    private final BuyerRepository buyers;
    private final AdminRepository admins;
    private final BCryptPasswordEncoder encoder;
    private final JwtService jwt;
    private final ActivityLogService activity;
    private final UserDirectory directory;
    private final AppProperties props;

    public AuthService(FarmerRepository farmers, BuyerRepository buyers, AdminRepository admins,
                       BCryptPasswordEncoder encoder, JwtService jwt, ActivityLogService activity,
                       UserDirectory directory, AppProperties props) {
        this.farmers = farmers;
        this.buyers = buyers;
        this.admins = admins;
        this.encoder = encoder;
        this.jwt = jwt;
        this.activity = activity;
        this.directory = directory;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String phone = UserDirectory.normalizePhone(req.phone());
        if (phone == null || !phone.matches("^0[17]\\d{8}$")) {
            throw ApiException.badRequest("phone: must be a valid Kenyan phone number e.g. 0712345678");
        }
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase();
        User user;
        if (req.role() == Role.FARMER) {
            if (farmers.existsByPhone(phone)) throw ApiException.conflict("A farmer account with this phone number already exists");
            if (email != null && farmers.existsByEmailIgnoreCase(email)) throw ApiException.conflict("This email is already registered");
            Farmer f = new Farmer();
            f.setStatus(props.isAutoApproveFarmers() ? AccountStatus.ACTIVE : AccountStatus.PENDING);
            user = f;
        } else if (req.role() == Role.BUYER) {
            if (buyers.existsByPhone(phone)) throw ApiException.conflict("A buyer account with this phone number already exists");
            if (email != null && buyers.existsByEmailIgnoreCase(email)) throw ApiException.conflict("This email is already registered");
            Buyer b = new Buyer();
            b.setStatus(AccountStatus.ACTIVE);
            b.setPreferences(normalizePreferences(req.preferences()));
            user = b;
        } else {
            throw ApiException.badRequest("Only farmers and buyers can self-register");
        }
        user.setName(req.name().trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setLocation(CropCatalog.canonicalCounty(req.location()));
        user.setPasswordHash(encoder.encode(req.password()));
        user.setLastLoginAt(LocalDateTime.now());
        user = directory.save(user);
        activity.log(user, "REGISTER", user.getRole() + " registered from " + user.getLocation()
                + (user.getStatus() == AccountStatus.PENDING ? " (awaiting approval)" : ""));
        return new AuthResponse(jwt.issue(user), view(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String id = req.phone().trim();
        List<User> candidates = new ArrayList<>();
        if (id.contains("@")) {
            admins.findByEmailIgnoreCase(id).ifPresent(candidates::add);
            farmers.findByEmailIgnoreCase(id).ifPresent(candidates::add);
            buyers.findByEmailIgnoreCase(id).ifPresent(candidates::add);
        } else {
            String phone = UserDirectory.normalizePhone(id);
            Optional<Farmer> f = farmers.findByPhone(phone);
            Optional<Buyer> b = buyers.findByPhone(phone);
            if (req.role() == Role.BUYER) {
                b.ifPresent(candidates::add);
                f.ifPresent(candidates::add);
            } else {
                f.ifPresent(candidates::add);
                b.ifPresent(candidates::add);
            }
            admins.findByPhone(phone).ifPresent(candidates::add);
        }
        User user = candidates.stream().filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .findFirst().orElseThrow(() -> ApiException.unauthorized("Incorrect phone number or password"));
        if (user.getStatus() == AccountStatus.BANNED) throw ApiException.forbidden("This account has been banned. Contact the administrator.");
        if (user.getStatus() == AccountStatus.SUSPENDED) throw ApiException.forbidden("This account is suspended. Contact the administrator.");
        user.setLastLoginAt(LocalDateTime.now());
        directory.save(user);
        activity.log(user, "LOGIN", user.getRole() + " logged in");
        return new AuthResponse(jwt.issue(user), view(user));
    }

    public UserView me() {
        return view(directory.current());
    }

    @Transactional
    public UserView updateProfile(UpdateProfileRequest req) {
        User u = directory.current();
        if (req.name() != null && !req.name().isBlank()) u.setName(req.name().trim());
        if (req.email() != null) u.setEmail(req.email().isBlank() ? null : req.email().trim().toLowerCase());
        if (req.location() != null && !req.location().isBlank()) u.setLocation(CropCatalog.canonicalCounty(req.location()));
        if (u instanceof Buyer b && req.preferences() != null) b.setPreferences(normalizePreferences(req.preferences()));
        u = directory.save(u);
        activity.log(u, "PROFILE_UPDATE", "Profile updated");
        return view(u);
    }

    public static UserView view(User u) {
        String prefs = u instanceof Buyer b ? b.getPreferences() : null;
        return new UserView(u.getId(), u.getRole(), u.getName(), u.getPhone(), u.getEmail(), u.getLocation(), prefs,
                u.getStatus(), u.getCreatedAt(), u.getLastLoginAt());
    }

    static String normalizePreferences(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(CropCatalog::canonical).distinct().collect(Collectors.joining(","));
    }
}
