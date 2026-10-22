package aisafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlightOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightOpsApplication.class, args);
	}

}
