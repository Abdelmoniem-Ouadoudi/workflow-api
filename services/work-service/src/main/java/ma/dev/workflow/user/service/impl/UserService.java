package ma.dev.workflow.user.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.user.dto.UserDTO;
import ma.dev.workflow.user.dto.mapper.UserMapper;
import ma.dev.workflow.user.models.User;
import ma.dev.workflow.user.models.enums.Role;
import ma.dev.workflow.user.repositories.UserRepository;
import ma.dev.workflow.user.service.IUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService implements IUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public List<UserDTO> findAll() {
        return userMapper.fromModelList(userRepository.findAll());
    }

    @Override
    public UserDTO findById(Long id) {
        return userMapper.fromModel(getOrThrow(id));
    }

    @Override
    @Transactional
    public UserDTO create(UserDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BusinessRuleException("USERNAME_TAKEN",
                    "Username already used: " + dto.getUsername());
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessRuleException("EMAIL_TAKEN",
                    "Email already used: " + dto.getEmail());
        }
        User user = userMapper.fromDTO(dto);
        // Defaults to active so nothing that created a user before M5 changes behaviour.
        // auth-service now sends false: registration creates a profile for somebody an
        // administrator has not approved yet, and an unapproved person must not be assignable.
        user.setActive(dto.getActive() == null || dto.getActive());
        return userMapper.fromModel(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDTO update(Long id, UserDTO dto) {
        User user = getOrThrow(id);
        if (userRepository.existsByUsernameAndIdNot(dto.getUsername(), id)) {
            throw new BusinessRuleException("USERNAME_TAKEN",
                    "Username already used: " + dto.getUsername());
        }
        if (userRepository.existsByEmailAndIdNot(dto.getEmail(), id)) {
            throw new BusinessRuleException("EMAIL_TAKEN",
                    "Email already used: " + dto.getEmail());
        }
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        // The role is deliberately NOT set here any more.
        //
        // This was a real bug until M5. A token is minted from account.role in authdb; this column
        // is only a copy. Changing it here promoted somebody in the profile while every token they
        // were issued went on saying the old role - for as long as the account existed, because
        // nothing ever wrote the authoritative column back. The role now changes in auth-service
        // and arrives here through setRole below, so there is one writer and no way to disagree.
        return userMapper.fromModel(userRepository.save(user));
    }

    /**
     * Mirrors the global role after auth-service has changed it. SERVICE-only.
     *
     * <p>Not reachable from a browser. auth-service owns the role because auth-service is what puts
     * it in a token; this column exists so work-service can show a role without a network call.
     */
    @Override
    @Transactional
    public UserDTO setRole(Long id, Role role) {
        User user = getOrThrow(id);
        user.setRole(role);
        return userMapper.fromModel(userRepository.save(user));
    }

    /** Mirrors the account status after an administrator approved, disabled or re-enabled it. */
    @Override
    @Transactional
    public UserDTO setActive(Long id, boolean active) {
        User user = getOrThrow(id);
        user.setActive(active);
        return userMapper.fromModel(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDTO deactivate(Long id) {
        User user = getOrThrow(id);
        user.setActive(false);
        return userMapper.fromModel(userRepository.save(user));
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
}
