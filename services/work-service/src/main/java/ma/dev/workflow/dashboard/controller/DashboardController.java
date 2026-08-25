package ma.dev.workflow.dashboard.controller;

import ma.dev.workflow.dashboard.dto.DashboardDTO;
import ma.dev.workflow.dashboard.service.IDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole dashboard in one call.
 *
 * <p>Readable by anyone signed in, not just an admin. These are numbers about the team's own work,
 * and hiding them from the people doing it would be a strange kind of secrecy.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final IDashboardService dashboardService;

    public DashboardController(IDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardDTO load() {
        return dashboardService.load();
    }
}
