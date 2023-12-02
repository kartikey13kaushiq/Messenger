package com.myproject.app.messanger.service;


import com.myproject.app.messanger.model.Users;
import com.myproject.app.messanger.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public Users registerUser(Users user) {
        // Add validation logic and password encryption in a real-world scenario
        return userRepository.save(user);
    }

    public Users getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}