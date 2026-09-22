package com.uttkarsh.billsync.service;

import com.uttkarsh.billsync.domain.User;
import com.uttkarsh.billsync.dto.CreateUserRequest;
import com.uttkarsh.billsync.dto.UserResponse;
import com.uttkarsh.billsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository users;

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
}
