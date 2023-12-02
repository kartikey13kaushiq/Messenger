package com.myproject.app.messanger.controller;

import com.myproject.app.messanger.model.Users;
import com.myproject.app.messanger.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/user/register")
    public String registerUser(@RequestParam String username, @RequestParam String password) {
        Users user = new Users();
        user.setPassword(password);
        user.setUsername(username);
        System.out.println("Kartikey Debug UserController-->");
        System.out.println(user.getId());
        System.out.println(user.getPassword());
        System.out.println(user.getUsername());
        System.out.println("Kartikey Debug UserController-->");
        userService.registerUser(user);
        return "redirect:/login"; // You might want to redirect to a login page or another page after registration
    }
}
