package com.gustavo.keycloakUserSync;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class CustomEventListenerProvider implements EventListenerProvider {
	
	private KeycloakSession session;
	
	private ObjectMapper objectMapper;
	public ConnectionFactory factory;
	
	Logger log = LoggerFactory.getLogger(CustomEventListenerProvider.class);
		
	public CustomEventListenerProvider(KeycloakSession session) {
		this.session = session;
		this.objectMapper = new ObjectMapper();
		this.factory = new ConnectionFactory();
		this.factory.setPort(5672);
		this.factory.setHost("localhost");
		this.factory.setUsername("user");
		this.factory.setPassword("password");
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onEvent(Event event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onEvent(AdminEvent event, boolean includeRepresentation) {
		if("USER".equals(event.getResourceType().name())) {
			if ("CREATE".equals(event.getOperationType().name())) {
				log.info("USER CREATE: " + event.getRepresentation());
				
				String userId = event.getResourcePath().substring("users/".length());
	            UserModel userModel = session.users().getUserById(session.getContext().getRealm(), userId);
	            
	            UserEventDto userEventDto = new UserEventDto(userModel);
	    		userEventDto.setActionType("CREATE");
	    		
	    		publishUser(userEventDto);						
			
			} else if ("UPDATE".equals(event.getOperationType().name())) {
				log.info("USER UPDATE: " + event.getRepresentation());
			} else if("DELETE".equals(event.getOperationType().name())) {
				String userId = event.getResourcePath().substring("users/".length());
				log.info("USER DELETE: " + userId);
			}
		}
	}
	
	private void publishUser(UserEventDto userEventDto) {

		try(Connection connection = factory.newConnection()){
			String message = objectMapper.writeValueAsString(userEventDto);
			
			Channel channel = connection.createChannel();
			channel.exchangeDeclare("users.v1.user_event", "direct");
			
			channel.basicPublish("users.v1.user_event", "users.v1.user_event.send_user.key", null, message.getBytes("UTF-8"));
			
			log.info("keycloak-to-rabbitmq SUCCESS sending message: %s%n", "users.v1.user_event.send_user.key");
		} catch (Exception ex) {
			log.error("keycloak-to-rabbitmq ERROR sending message: %s%n", "users.v1.user_event.send_user.key", ex);
		}
	}
}
