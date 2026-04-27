package com.invest.domain.ports.out;

import com.invest.domain.entities.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);
}
