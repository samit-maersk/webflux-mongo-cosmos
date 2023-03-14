package net.samitkumarpatel.webfluxmongocosmos;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class WebfluxMongoCosmosApplication {
	final CustomerRepository customerRepository;
	public static void main(String[] args) {
		SpringApplication.run(WebfluxMongoCosmosApplication.class, args);
	}

	// print all the details in the log
	@Bean
	public ObservationTextPublisher printingObservationHandler() {
		return new ObservationTextPublisher();
	}

	@Bean
	public RouterFunction<ServerResponse> route(Handler handler) {
		return RouterFunctions
				.route()
				.path("/customer", builder ->
						builder
								.GET("/all", handler::all)
								.GET("/{id}", handler::customerById )
								.POST("", handler::save)
								.PUT("/{id}", handler::update)
								.DELETE("/{id}", handler::delete))
				.build();
	}
}


@Component
@Slf4j
record Handler(CustomerService customerService) {
	public Mono<ServerResponse> all(ServerRequest request) {
		return ok().body(customerService.getAll().collectList(), List.class);
	}

	public Mono<ServerResponse> customerById(ServerRequest request) {
		var id = request.pathVariable("id");
		return ok().body(customerService.getById(id), Customer.class);
	}

	public Mono<ServerResponse> save(ServerRequest request) {
		return request
				.bodyToMono(Customer.class)
				.flatMap(customer -> ok().body(customerService.add(customer), Customer.class));
	}

	public Mono<ServerResponse> update(ServerRequest request) {
		var id = request.pathVariable("id");
		return request
					.bodyToMono(Customer.class)
					.doOnNext(customer -> log.info("request customer : {}", customer))
					.flatMap(customer -> ok().body(customerService.update(id, customer), Customer.class));
	}

	public Mono<ServerResponse> delete(ServerRequest request) {
		var id = request.pathVariable("id");
		return ok().body(customerService.delete(id), Void.class);
	}
}

interface CustomerService {
	Flux<Customer> getAll();
	Mono<Customer> getById(String id);
	Mono<Customer> add(Customer customer);
	Mono<Customer> update(String id, Customer customer);
	Mono<Void> delete(String id);
}
@Service
@Slf4j
@AllArgsConstructor
class CustomerServiceImpl implements CustomerService {
	private final Supplier<Long> latency = () -> new Random().nextLong(500);
	final CustomerRepository customerRepository;

	final ObservationRegistry registry;


	@Override
	public Flux<Customer> getAll() {
		Long lat = latency.get();
		return customerRepository
				.findAll()
				.delayElements(Duration.ofMillis(lat))
				.name("getAll.call")
				.tag("latency", lat > 250 ? "high" : "low")
				.tap(Micrometer.observation(registry));
	}
	@Override
	public Mono<Customer> getById(String id) {
		//TODO handle empty 200 scenario from findById
		Long lat = latency.get();
		return customerRepository
				.findById(id)
				.delayElement(Duration.ofMillis(lat));
	}
	@Override
	public Mono<Customer> add(Customer customer) {
		Long lat = latency.get();
		return customerRepository
				.save(customer)
				.delayElement(Duration.ofMillis(lat));
	}

	@Override
	public Mono<Customer> update(String id, Customer customer) {
		Long lat = latency.get();
		return customerRepository
				.findById(id)
				.onErrorResume(e -> Mono.error(e))
				.doOnNext(customer1 -> log.info("update: db data for id:{} is : {}",id, customer1))
				//TODO handle empty 200 scenario from findById
				.flatMap(customer1 -> {
					log.info("update: going to be update with : {}", customer);
					var customerWithUpdateData = Customer.builder().id(customer1.id()).name(customer.name()).age(customer.age()).build();
					return add(customerWithUpdateData);
				})
				.delayElement(Duration.ofMillis(lat));
	}
	@Override
	public Mono<Void> delete(String id) {
		return customerRepository.deleteById(id);
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

@ResponseStatus(HttpStatus.NOT_FOUND)
class DataNotFoundException extends RuntimeException {
	public DataNotFoundException(String message) {
		super(message);
	}
}