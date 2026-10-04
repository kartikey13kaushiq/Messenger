package com.myproject.app.messenger.realtime;

import com.myproject.app.messenger.config.JwtService;
import com.myproject.app.messenger.user.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /ws}. Clients authenticate in the CONNECT frame with
 * {@code Authorization: Bearer <token>} and may only subscribe to their own {@code /user/queue/...}
 * destinations. Uses the in-memory broker; with several instances, switch to a broker relay
 * (RabbitMQ or ActiveMQ STOMP) so events reach users connected to other nodes.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final String[] allowedOrigins;

    WebSocketConfig(JwtService jwtService, @Value("${app.websocket.allowed-origins:*}") String[] allowedOrigins) {
        this.jwtService = jwtService;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (stomp == null || stomp.getCommand() == null) {
                    return message;
                }
                StompCommand command = stomp.getCommand();
                if (command == StompCommand.CONNECT) {
                    String header = stomp.getFirstNativeHeader("Authorization");
                    String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
                    AuthUser user = token == null ? null : jwtService.verify(token).orElse(null);
                    if (user == null) {
                        throw new MessagingException("A valid bearer token is required");
                    }
                    stomp.setUser(user);
                } else if (command == StompCommand.SUBSCRIBE) {
                    String destination = stomp.getDestination();
                    if (stomp.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessagingException("Subscriptions are limited to your own /user/queue destinations");
                    }
                } else if (command == StompCommand.SEND && stomp.getUser() == null) {
                    throw new MessagingException("Not connected");
                }
                return message;
            }
        });
    }
}
