package ke.ac.strathmore.fmls.controller;

import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.FarmerDashboard;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farmer")
@RequireRole(Role.FARMER)
public class FarmerController {

    private final DashboardService dashboard;

    public FarmerController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public FarmerDashboard dashboard() {
        return dashboard.farmer();
    }
}
