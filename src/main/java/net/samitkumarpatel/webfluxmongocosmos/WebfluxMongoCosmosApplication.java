package net.samitkumarpatel.webfluxmongocosmos;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class WebfluxMongoCosmosApplication {
	final CustomerRepository customerRepository;
	public static void main(String[] args) {
		SpringApplication.run(WebfluxMongoCosmosApplication.class, args);
	}

	@Bean
	public RouterFunction<ServerResponse> route() {
		return RouterFunctions
				.route()
				.GET("/customer/all", request -> ok().body(customerRepository.findAll().collectList(), List.class))
				.GET("/customer/{id}", request -> ok().body(customerRepository.findById(request.pathVariable("id")), Customer.class))
				.POST("/customer", request -> {
					return request
							.bodyToMono(Customer.class)
							.flatMap(customer -> ok().body(customerRepository.save(customer), Customer.class));
				})
				.PUT("/customer/{id}", request -> {
					var id = request.pathVariable("id");
					return customerRepository
							.findById(id)
							.doOnNext(c -> log.info("db customer : {}",c))
							.flatMap(customer -> {
								return request
										.bodyToMono(Customer.class)
										.doOnNext(c -> log.info("request customer : {}"))
										.map(customer1 -> Customer.builder().id(customer.id()).name(customer1.name()).age(customer1.age()).build())
										.flatMap(customer2 -> ok().body(customerRepository.save(customer2), Customer.class));
							});
				})
				.DELETE("/customer/{id}", request -> {
					var id = request.pathVariable("id");
					return ok().body(customerRepository.deleteById(id), Void.class);
				})
				.build();
	}
}

interface CustomerRepository extends ReactiveMongoRepository<Customer,String> {}

record Customer(
		@Id
		String id,
		String name,
		int age) {
	@Builder
	public Customer {}
}