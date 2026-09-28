package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.Admin;
import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.User;
import ke.ac.strathmore.fmls.repository.AdminRepository;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.FarmerRepository;
import ke.ac.strathmore.fmls.security.AuthContext;
import ke.ac.strathmore.fmls.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Looks users up across the farmers, buyers and admins tables. */
@Service
public class UserDirectory {

    private final FarmerRepository farmers;
    private final BuyerRepository buyers;
    private final AdminRepository admins;

    public UserDirectory(FarmerRepository farmers, BuyerRepository buyers, AdminRepository admins) {
        this.farmers = farmers;
        this.buyers = buyers;
        this.admins = admins;
    }

    public Optional<User> find(Role role, Long id) {
        if (role == null || id == null) return Optional.empty();
        return switch (role) {
            case FARMER -> farmers.findById(id).map(u -> (User) u);
            case BUYER -> buyers.findById(id).map(u -> (User) u);
            case ADMIN -> admins.findById(id).map(u -> (User) u);
        };
    }

    public User save(User user) {
        if (user instanceof Farmer f) return farmers.save(f);
        if (user instanceof Buyer b) return buyers.save(b);
        return admins.save((Admin) user);
    }

    public Farmer currentFarmer() {
        CurrentUser u = AuthContext.require(Role.FARMER);
        return farmers.findById(u.id()).orElseThrow(() -> ApiException.unauthorized("Farmer account not found"));
    }

    public Buyer currentBuyer() {
        CurrentUser u = AuthContext.require(Role.BUYER);
        return buyers.findById(u.id()).orElseThrow(() -> ApiException.unauthorized("Buyer account not found"));
    }

    public User current() {
        CurrentUser u = AuthContext.get();
        return find(u.role(), u.id()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
    }

    /** Normalises Kenyan phone numbers: "+254 712 345 678" / "254712345678" / "712345678" → "0712345678". */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("254") && digits.length() == 12) digits = "0" + digits.substring(3);
        if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) digits = "0" + digits;
        return digits;
    }
}
