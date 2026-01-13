package com.message.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.dto.Message;
import com.message.repository.MessageRepository;
import com.message.session.WebSocketSessionManager;

@Service
public class MessageService {

	private static final Logger log = LoggerFactory.getLogger(MessageService.class);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final WebSocketSessionManager webSocketSessionManager;
	private final MessageDataService messageDataService;
	private final MessageRepository messageRepository;

	public MessageService(
		WebSocketSessionManager webSocketSessionManager,
		MessageDataService messageDataService,
		MessageRepository messageRepository
	) {
		this.webSocketSessionManager = webSocketSessionManager;
		this.messageDataService = messageDataService;
		this.messageRepository = messageRepository;
	}

	public Optional<Message> getLastMessage() {
		return messageRepository.findTopByOrderByMessageSequenceDesc()
			.map(messageEntity -> new Message(
				messageEntity.getUsername(),
				messageEntity.getMessageSequence() + ":" + messageEntity.getContent()));
	}

	/*
	  - 메서드에 파라미터가 존재하지 않으면 @Cacheable 사용할 수 없음
	  - key: 파라미터가 1개일 때는 생략 가능
	  - unless:
	    - 데이터베이스에 데이터가 존재하지 않으면 Optional.empty()가 넘어올테고,
	      해당 속성을 설정하지 않으면 Optional.empty()를 캐시에 쓰려함
	    - 캐시 설정에 Null 값은 쓰지 못하도록 막아놨기 때문에 에러가 발생한다
	    - unless = "#result == null" 와 같이 설정하면 Optional.empty()도 필터링되어 캐싱되지 않는다
	 */
	@Cacheable(value = "message", unless = "#result == null")
	public Optional<Message> getMessage(Long messageSequenceId) {
		return messageRepository.findById(messageSequenceId)
			.map(messageEntity -> new Message(
				messageEntity.getUsername(),
				messageEntity.getMessageSequence() + ":" + messageEntity.getContent()));
	}

	/*
	  @CacheEvict: 특정 조건에 맞으면 캐시 데이터 삭제
	 */
	@CacheEvict(value = "message", allEntries = true)
	@Transactional
	public void sendMessageToAll(WebSocketSession senderSession, String payload) {
		try {
			Message receivedMessage = objectMapper.readValue(payload, Message.class);
			boolean makeException = receivedMessage.content().contains("/exception");
			messageDataService.save(receivedMessage, makeException);

			webSocketSessionManager.getSessions().forEach(participantSession -> {
				if (!senderSession.getId().equals(participantSession.getId())) {
					sendMessage(participantSession, receivedMessage);
				}
			});
		} catch (Exception e) {
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			}

			String errorMessage = "Invalid protocol";
			log.error("ErrorMessage payload: {} from {}", payload, senderSession.getId());
			sendMessage(senderSession, new Message("system", errorMessage));
		}
	}

	public void sendMessage(WebSocketSession session, Message message) {
		try {
			String msg = objectMapper.writeValueAsString(message);
			session.sendMessage(new TextMessage(msg));
			log.info("Send message: {} to {}", msg, session.getId());
		} catch (Exception e) {
			log.error("Failed to send message to {} error: {}", session.getId(), e.getMessage());
		}
	}
}
