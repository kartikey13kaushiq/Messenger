package com.myproject.app.messanger.service;

import org.springframework.stereotype.Service;

@Service
public class MessageService {

    public String getMessage() {
        return "This is a message from the MessageService!";
    }
}
