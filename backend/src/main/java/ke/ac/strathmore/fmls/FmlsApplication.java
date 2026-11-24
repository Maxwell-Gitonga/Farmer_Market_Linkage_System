package ke.ac.strathmore.fmls;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Web-Based Farmer Market Linkage System with Integrated Decision Support
 * for Price Optimization and Demand Analysis – Spring Boot backend.
 *
 * Angular frontend  --REST-->  this backend  --JPA-->  MySQL
 *                                   |
 *                                   +--REST-->  Python ML module (price prediction, demand forecasting,
 *                                                recommendation engine)
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class FmlsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FmlsApplication.class, args);
    }
}