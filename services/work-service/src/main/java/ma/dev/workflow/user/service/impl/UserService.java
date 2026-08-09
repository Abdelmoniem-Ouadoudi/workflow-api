package ma.dev.workflow.user.service.impl;

import jakarta.persistence.EntityNotFoundException;
import ma.dev.workflow.common.exception.BusinessRuleException;
import ma.dev.workflow.user.dto.UserDTO;
import ma.dev.workflow.user.dto.mapper.UserMapper;
import ma.dev.workflow.user.models.User;
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
        user.setActive(true);
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
        user.setRole(dto.getRole());
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
