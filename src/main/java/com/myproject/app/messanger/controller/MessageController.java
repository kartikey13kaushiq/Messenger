package com.myproject.app.messanger.controller;

import com.myproject.app.messanger.model.Message;
import com.myproject.app.messanger.model.Users;
import com.myproject.app.messanger.service.MessageService;
import com.myproject.app.messanger.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/message")
public class MessageController {
    @Autowired
    private MessageService messageService;
    @Autowired
    private UserService userService;

    @GetMapping("/inbox")
    public String getInbox(Model model, Principal principal) {
        String username = principal.getName();
        Users receiver = userService.getUserByUsername(username);
        List<Message> messages = messageService.getMessagesForUser(receiver);
        model.addAttribute("messages", messages);
        return "inbox"; // Create Thymeleaf template for displaying inbox
    }

    @PostMapping("/send")
    public String sendMessage(@ModelAttribute Message message, Principal principal) {
        String username = principal.getName();
        Users sender = userService.getUserByUsername(username);
        message.setSender(sender);
        messageService.sendMessage(message);
        return "redirect:/message/inbox";
    }
}
