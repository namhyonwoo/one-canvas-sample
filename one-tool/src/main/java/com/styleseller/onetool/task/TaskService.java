package com.styleseller.onetool.task;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.stereotype.Service;

/**
 * 블로킹 JDBC 호출을 리액티브 파이프라인에 안전하게 얹는 경계.
 * <p>
 * 저장소 계층이 JdbcTemplate/JdbcClient로 쓰여 있어 블로킹이다.
 * 이 호출이 Netty의 event loop 스레드를 점유하면 서버 전체가 멈추므로,
 * 모든 DB 접근을 {@link Schedulers#boundedElastic()}으로 넘긴다.
 */
@Service
public class TaskService {

	private final TaskRepository repository;

	private final Scheduler jdbcScheduler = Schedulers.boundedElastic();

	public TaskService(TaskRepository repository) {
		this.repository = repository;
	}

	public Flux<Task> findAll() {
		return Mono.fromCallable(this.repository::findAll)
				.subscribeOn(this.jdbcScheduler)
				.flatMapMany(Flux::fromIterable);
	}

	public Mono<Task> findById(long id) {
		return Mono.fromCallable(() -> this.repository.findById(id))
				.subscribeOn(this.jdbcScheduler)
				.flatMap(Mono::justOrEmpty);
	}

	public Mono<Task> create(String title, String status) {
		return Mono.fromCallable(() -> this.repository.create(title, status))
				.subscribeOn(this.jdbcScheduler);
	}
}
