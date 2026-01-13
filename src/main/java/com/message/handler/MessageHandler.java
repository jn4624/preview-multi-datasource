package com.message.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.message.dto.Message;
import com.message.service.MessageService;
import com.message.session.WebSocketSessionManager;

@Component
public class MessageHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
	private final WebSocketSessionManager webSocketSessionManager;
	private final MessageService messageService;

	public MessageHandler(WebSocketSessionManager webSocketSessionManager, MessageService messageService) {
		this.webSocketSessionManager = webSocketSessionManager;
		this.messageService = messageService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		log.info("ConnectionEstablished: {}", session.getId());

		/*
		  - 현재 sendMessage는 Thread-safe 하지 않다.
		  - 이를 Thread-safe하게 하기 위해 Spring에서 제공하는 ConcurrentWebSocketSessionDecorator를 활용해야 한다.
		  - bufferSizeLimit은 초과하면 기본 정책이 terminate라 session을 닫는다.
		  - 동작 방식: 프록시 역할로 다른 멀티 스레드들이 sendMessage를 호출할 때
		    ConcurrentWebSocketSessionDecorator가 내부에서 buffer queue에 받아놓고 하나씩 꺼내서 sendMessage를 대신 호출한다.
		 */
		ConcurrentWebSocketSessionDecorator concurrentWebSocketSessionDecorator =
			new ConcurrentWebSocketSessionDecorator(session, 5000, 100 * 1024);

		webSocketSessionManager.storeSession(concurrentWebSocketSessionDecorator);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.error("TransportError: [{}] from {}", exception.getMessage(), session.getId());
		webSocketSessionManager.terminateSession(session.getId());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
		log.info("ConnectionClosed: [{}] from {}", status, session.getId());

		/*
		  - WebSocketSession이 닫히는 경우 afterConnectionClosed가 호출된다.
		  - 서버에서 세션을 닫는 경우에도 afterConnectionClosed가 호출된다.
		  - 이 경우 무한 루프 발생 가능성을 예상하겠지만
		    terminateSession가 중복 호출되어도 메서드 내부 sessions 맵에
		    session이 존재하는 경우에만 close 메서드가 호출되므로 무한 루프가 발생하지 않는다.
		 */
		webSocketSessionManager.terminateSession(session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession senderSession, @NonNull TextMessage message) {
		log.info("Received TextMessage: [{}] from {}", message, senderSession.getId());

		String payload = message.getPayload();

		if (payload.equals("/last")) {
			messageService.getLastMessage()
				.ifPresent(msg -> messageService.sendMessage(senderSession, msg));
		} else if (payload.contains("/get")) {
			String[] split = payload.split(" ");

			if (split.length > 1) { // ex: /get {number}
				try {
					messageService.getMessage(Long.valueOf(split[1]))
						.ifPresent(msg -> messageService.sendMessage(senderSession, msg));
				} catch (Exception e) {
					String errorMessage = "Invalid protocol";
					log.error("Get request failed. cause: {}", e.getMessage());
					messageService.sendMessage(senderSession, new Message("system", errorMessage));
				}
			}
		} else {
			try {
				messageService.sendMessageToAll(senderSession, payload);
			} catch (Exception e) {
				log.error("Failed to send message to {} error: {}", senderSession.getId(), e.getMessage());
			}
		}
	}
}
