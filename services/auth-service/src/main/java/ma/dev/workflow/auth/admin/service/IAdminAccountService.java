package ma.dev.workflow.auth.admin.service;

import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.models.enums.Role;
import ma.dev.workflow.auth.admin.dto.AdminAccountDTO;

import java.util.List;

public interface IAdminAccountService {

    /** Every account, or only those in one status. Enriched with the profile's email. */
    List<AdminAccountDTO> findAll(AccountStatus status);

    /** PENDING to ACTIVE, with the role the administrator chose. */
    AdminAccountDTO approve(Long id, Role role);

    /** PENDING to DISABLED. The row is kept: "we said no" is part of the record. */
    AdminAccountDTO reject(Long id);

    AdminAccountDTO changeRole(Long id, Role role);

    AdminAccountDTO setStatus(Long id, AccountStatus status);
}
