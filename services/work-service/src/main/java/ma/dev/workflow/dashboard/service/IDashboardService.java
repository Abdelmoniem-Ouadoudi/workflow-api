package ma.dev.workflow.dashboard.service;

import ma.dev.workflow.dashboard.dto.DashboardDTO;

public interface IDashboardService {

    /** Every metric on the screen, read in one go so the numbers are all from one moment. */
    DashboardDTO load();
}
