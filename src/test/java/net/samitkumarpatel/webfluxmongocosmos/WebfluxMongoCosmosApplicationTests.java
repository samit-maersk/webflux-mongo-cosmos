package net.samitkumarpatel.webfluxmongocosmos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
class WebfluxMongoCosmosApplicationTests {

	@Container
	static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");

	@Autowired
	CustomerRepository customerRepository;
	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.database", () -> "db");
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}
	@BeforeEach
	void setUp() {}

	@AfterEach
	void tearDown() {}

	@Test
	void contextLoads() {
	}

	@Test
	void repositoryTest() {
		customerRepository
				.save(Customer.builder().name("Test").age(30).build())
				.as(StepVerifier::create)
				.consumeNextWith(customer1 -> {
					assertNotNull(customer1);
					assertNotNull(customer1.id());
					assertEquals("Test", customer1.name());
					assertEquals(30, customer1.age());
				})
				.verifyComplete();

	}



}
