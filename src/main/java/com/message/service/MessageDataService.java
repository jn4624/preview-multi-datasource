package com.message.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.message.dto.Message;
import com.message.entity.MessageEntity;
import com.message.repository.MessageRepository;

@Service
public class MessageDataService {

	private static final Logger log = LoggerFactory.getLogger(MessageDataService.class);

	private final MessageRepository messageRepository;

	public MessageDataService(MessageRepository messageRepository) {
		this.messageRepository = messageRepository;
	}

	@Transactional
	public void save(Message message, boolean makeException) {
		try {
			messageRepository.save(new MessageEntity(message.username(), message.content()));

			if (makeException) {
				throw new RuntimeException("For test");
			}
		} catch (Exception e) {
			log.error("Message save failed. cause: {}", e.getMessage());
			throw e;
		}
	}
}
