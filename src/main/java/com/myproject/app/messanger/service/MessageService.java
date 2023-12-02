package com.myproject.app.messanger.service;

import com.myproject.app.messanger.model.Message;
import com.myproject.app.messanger.model.Users;
import com.myproject.app.messanger.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {
    @Autowired
    private MessageRepository messageRepository;

    public List<Message> getMessagesForUser(Users receiver) {
        return messageRepository.findByReceiverOrderByTimestampDesc(receiver);
    }

    public Message sendMessage(Message message) {
        message.setTimestamp(LocalDateTime.now());
        return messageRepository.save(message);
    }
}
