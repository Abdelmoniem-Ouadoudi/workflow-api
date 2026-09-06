package ma.dev.workflow.auth.account.repositories;

import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    /** The administrator's queue: everybody waiting, oldest first, so nobody is left behind. */
    List<Account> findByStatusOrderByCreatedAtAsc(AccountStatus status);

    List<Account> findAllByOrderByCreatedAtDesc();
}
