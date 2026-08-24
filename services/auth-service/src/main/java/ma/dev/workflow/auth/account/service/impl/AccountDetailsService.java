package ma.dev.workflow.auth.account.service.impl;

import ma.dev.workflow.auth.account.models.Account;
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

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
                .disabled(!account.getActive())
                .build();
    }
}
