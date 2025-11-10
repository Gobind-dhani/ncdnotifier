package com.ibsec.ncdnotifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NcdnotifierApplication {

	public static void main(String[] args) {
		SpringApplication.run(NcdnotifierApplication.class, args);
	}

}
