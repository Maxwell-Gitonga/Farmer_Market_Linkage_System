package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.MarketPrice;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.AdminOverview;
import ke.ac.strathmore.fmls.dto.AdminUserView;
import ke.ac.strathmore.fmls.dto.ImportResult;
import ke.ac.strathmore.fmls.dto.LogView;
import ke.ac.strathmore.fmls.dto.MarketPriceRequest;
import ke.ac.strathmore.fmls.dto.ProductView;
import ke.ac.strathmore.fmls.dto.RemoveListingRequest;
import ke.ac.strathmore.fmls.dto.ReportView;
import ke.ac.strathmore.fmls.dto.UserStatusRequest;
import ke.ac.strathmore.fmls.module.MarketDataModule;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.ActivityLogService;
import ke.ac.strathmore.fmls.service.AdminService;
import ke.ac.strathmore.fmls.service.ApiException;
import ke.ac.strathmore.fmls.service.MlAdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Administrator functions (use case diagram: Manage users, Monitor system activity, ...). */
@RestController
@RequestMapping("/api/admin")
@RequireRole(Role.ADMIN)
public class AdminController {

    private final AdminService admin;
    private final MlAdminService mlAdmin;
    private final MarketDataModule market;
    private final ActivityLogService activity;

    public AdminController(AdminService admin, MlAdminService mlAdmin, MarketDataModule market,
                           ActivityLogService activity) {
        this.admin = admin;
        this.mlAdmin = mlAdmin;
        this.market = market;
        this.activity = activity;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    public List<AdminUserView> users(@RequestParam(required = false) Role role,
                                     @RequestParam(required = false) AccountStatus status) {
        return admin.users(role, status);
    }

    /** Approve / suspend / ban / reactivate: PATCH /api/admin/users/FARMER/12/status {"status":"ACTIVE"}. */
    @PatchMapping("/users/{role}/{id}/status")
    public AdminUserView setStatus(@PathVariable Role role, @PathVariable Long id,
                                   @Valid @RequestBody UserStatusRequest req) {
        return admin.setStatus(role, id, req.status(), req.reason());
    }

    @PostMapping("/farmers/{id}/approve")
    public AdminUserView approve(@PathVariable Long id) {
        return admin.setStatus(Role.FARMER, id, AccountStatus.ACTIVE, "approved");
    }

    @GetMapping("/listings")
    public List<ProductView> listings(@RequestParam(required = false) ProductStatus status) {
        return admin.listings(status);
    }

    @PostMapping("/listings/{id}/remove")
    public ProductView remove(@PathVariable Long id, @RequestBody(required = false) RemoveListingRequest req) {
        return admin.removeListing(id, req == null ? null : req.reason());
    }

    @GetMapping("/logs")
    public List<LogView> logs(@RequestParam(required = false) String action) {
        return admin.logs(action);
    }

    @GetMapping("/reports")
    public ReportView reports(@RequestParam(defaultValue = "365") int days) {
        return admin.reports(Math.min(Math.max(days, 7), 2000));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@RequestParam String type, @RequestParam(defaultValue = "90") int days) {
        String csv = admin.exportCsv(type, days);
        String name = "fmls-" + type + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/market-prices")
    public Map<String, Object> addPrice(@Valid @RequestBody MarketPriceRequest req) {
        MarketPrice m = market.addPrice(req);
        return Map.of("priceId", m.getPriceId(), "message", "Market price recorded");
    }

    @PostMapping("/market-prices/import")
    public ImportResult importPrices(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a CSV file");
        try {
            return market.importCsv(file.getInputStream());
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
    }

    @GetMapping("/ml/status")
    public Map<String, Object> mlStatus() {
        return mlAdmin.status();
    }

    /** Retrain the ML models on the current database contents (runs in the background). */
    @PostMapping("/ml/retrain")
    public Map<String, Object> retrain() {
        activity.log("ML_RETRAIN_REQUESTED", "Retraining requested by administrator");
        mlAdmin.retrainAsync("administrator");
        return Map.of("status", "STARTED", "message", "Retraining started – this takes about a minute.");
    }
}
