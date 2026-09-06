package ma.dev.workflow.auth.account.service.impl;

import ma.dev.workflow.auth.account.models.Account;
import ma.dev.workflow.auth.account.models.enums.AccountStatus;
import ma.dev.workflow.auth.account.repositories.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bridge between the {@code account} table and Spring Security.
 *
 * <p>{@code DaoAuthenticationProvider} asks this for a user, compares the submitted password
 * against the stored hash with the configured {@code PasswordEncoder}, and refuses a disabled
 * account before it ever looks at the password. Writing that comparison by hand would be four
 * lines, but it would also be four lines where a timing-safe comparison or a status check can be
 * quietly got wrong.
 */
@Service
@Transactional(readOnly = true)
public class AccountDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account named " + username));

        // Two flags rather than one, because the two refusals need different words on the screen.
        //
        // Spring Security has exactly two slots for "the password was right but you still may not
        // in": locked and disabled, which throw LockedException and DisabledException. Mapping
        // PENDING onto "locked" is a small stretch of the word - nobody locked anything - but it
        // is the standard slot, it costs one line, and GlobalExceptionHandler turns it into
        // ACCOUNT_PENDING. Writing a custom UserDetailsChecker to get a nicer name would be more
        // code in the one place worth keeping boring.
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
                .accountLocked(account.getStatus() == AccountStatus.PENDING)
                .disabled(account.getStatus() == AccountStatus.DISABLED)
                .build();
    }
}
