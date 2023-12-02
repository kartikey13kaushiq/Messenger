package com.myproject.app.messanger.repository;

import com.myproject.app.messanger.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {
    Users findByUsername(String username);
}