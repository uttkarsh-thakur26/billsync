package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.dto.CreateUserRequest;
import com.uttkarsh.billsync.dto.UpdateUserRequest;
import com.uttkarsh.billsync.dto.UserResponse;
import com.uttkarsh.billsync.repository.GroupMemberRepository;
import com.uttkarsh.billsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository users;
    private final GroupMemberRepository members;

    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with email " + email + " already exists");
        }
        return UserResponse.from(users.save(new User(request.name().trim(), email)));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }

    public UserResponse update(Long userId, UpdateUserRequest request) {
        User user = requireUser(userId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Name must not be blank");
            }
            user.setName(request.name().trim());
        }
        if (request.email() != null) {
            String email = request.email().trim();
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("A user with email " + email + " already exists");
            }
            user.setEmail(email);
        }
        return UserResponse.from(user);
    }

    /**
     * Only a person with no footprint can go: not in any group, and not named in any
     * expense or settlement. Group membership is checked here for a clear message;
     * the ledger references are left to the foreign keys, which cannot be bypassed.
     */
    public void delete(Long userId) {
        User user = requireUser(userId);
        if (members.existsByUserId(userId)) {
            throw new ConflictException(user.getName() + " is still in a group. Remove them from their groups first.");
        }
        try {
            users.delete(user);
            users.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(user.getName() + " appears in recorded expenses or settlements and cannot be deleted.");
        }
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }
}
