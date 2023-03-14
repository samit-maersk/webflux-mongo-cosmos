package net.samitkumarpatel.webfluxmongocosmos;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.configuration.IMockitoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

	@MockBean
	ObservationRegistry registry;
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

	@Test
	void serviceTest() {
		CustomerRepository customerRepository = Mockito.mock(CustomerRepository.class);
		CustomerService customerService = new CustomerServiceImpl(customerRepository, registry);
		//static data
		var customerOne = Customer.builder().id("1").name("One").age(30).build();
		var customerTwo = Customer.builder().id("2").name("Two").age(35).build();
		//mock
		when(customerRepository.save(any(Customer.class))).thenReturn(Mono.just(customerOne));
		when(customerRepository.findAll()).thenReturn(Flux.just(customerOne, customerTwo));
		when(customerRepository.deleteById(anyString())).thenReturn(Mono.empty().then());
		when(customerRepository.findById("3")).thenReturn(Mono.error(new DataNotFoundException("not found")));
		when(customerRepository.findById("1")).thenReturn(Mono.just(customerOne));

		customerService
				.getById("1")
				.as(StepVerifier::create)
				.consumeNextWith(customer -> {
					assertEquals(customerOne, customer);
				})
				.verifyComplete();

		customerService
				.add(Customer.builder().name("One").age(30).build())
				.as(StepVerifier::create)
				.consumeNextWith(customer -> {
					assertNotNull(customer);
					assertNotNull(customer.id());
				})
				.verifyComplete();

		//update
		var updateCustomer = Customer.builder().id("1").name("One-Update").age(35).build();
		when(customerRepository.save(updateCustomer)).thenReturn(Mono.just(updateCustomer));
		customerService
				.update("1", Customer.builder().name("One-Update").age(35).build())
				.as(StepVerifier::create)
				.consumeNextWith(customer -> {
					assertNotNull(customer);
					assertEquals("1", customer.id());
					assertEquals("One-Update", customer.name());
					assertEquals(35, customer.age());
				})
				.verifyComplete();

		//update 404
		customerService
				.update("3", Customer.builder().name("One-Update").age(35).build())
				.as(StepVerifier::create)
				.expectErrorMatches(e -> e instanceof DataNotFoundException)
				.verify();

	}
}
