package ma.dev.workflow.auth.admin.controller;

import jakarta.validation.Valid;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.admin.dto.AdminAccountDTO;
import ma.dev.workflow.auth.admin.dto.ApproveAccountRequest;
import ma.dev.workflow.auth.admin.dto.ChangeRoleRequest;
import ma.dev.workflow.auth.admin.service.IAdminAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The administrator's console.
 *
 * <p>Under {@code /admin/accounts} in <em>this</em> service, matching the split already used for
 * {@code /admin/issues} (work-service) and {@code /admin/classification} (classification-service):
 * an operator's controls live wherever the thing they operate lives. Accounts live here.
 *
 * <p>No {@code @PreAuthorize} — this service states its rules as path matchers in
 * {@code SecurityConfig}, where {@code /admin/accounts/**} requires ROLE_ADMIN. One place to read,
 * rather than a rule on the chain and another on a method.
 */
@RestController
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final IAdminAccountService adminAccountService;

    public AdminAccountController(IAdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /** {@code ?status=PENDING} is the approval queue; no parameter is everybody. */
    @GetMapping
    public List<AdminAccountDTO> findAll(@RequestParam(required = false) AccountStatus status) {
        return adminAccountService.findAll(status);
    }

    @PostMapping("/{id}/approve")
    public AdminAccountDTO approve(@PathVariable Long id,
                                   @Valid @RequestBody ApproveAccountRequest request) {
        return adminAccountService.approve(id, request.role());
    }

    @PostMapping("/{id}/reject")
    public AdminAccountDTO reject(@PathVariable Long id) {
        return adminAccountService.reject(id);
    }

    @PutMapping("/{id}/role")
    public AdminAccountDTO changeRole(@PathVariable Long id,
                                      @Valid @RequestBody ChangeRoleRequest request) {
        return adminAccountService.changeRole(id, request.role());
    }

    @PostMapping("/{id}/enable")
    public AdminAccountDTO enable(@PathVariable Long id) {
        return adminAccountService.setStatus(id, AccountStatus.ACTIVE);
    }

    @PostMapping("/{id}/disable")
    public AdminAccountDTO disable(@PathVariable Long id) {
        return adminAccountService.setStatus(id, AccountStatus.DISABLED);
    }
}
