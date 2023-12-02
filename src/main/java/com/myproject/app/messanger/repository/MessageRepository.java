package com.myproject.app.messanger.repository;

import com.myproject.app.messanger.model.Message;
import com.myproject.app.messanger.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByReceiverOrderByTimestampDesc(Users receiver);
}
