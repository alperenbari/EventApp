package com.event.event_ticket_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventTicketAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventTicketAppApplication.class, args);
	}

}
