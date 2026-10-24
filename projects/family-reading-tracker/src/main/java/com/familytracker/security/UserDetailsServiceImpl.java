package com.familytracker.security;

import com.familytracker.model.User;
import com.familytracker.repository.UserRepository;
import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepo;

    public UserDetailsServiceImpl(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepo
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // ⚡ Fix: If user role is null, assign "USER" role by default
        String role = user.getRole() != null ? user.getRole() : "USER";

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}
