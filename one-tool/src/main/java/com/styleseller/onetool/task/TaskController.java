package com.styleseller.onetool.task;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 설정이 실제로 동작하는지 확인하기 위한 샘플 엔드포인트. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService service;

	public TaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping
	public Flux<Task> list() {
		return this.service.findAll();
	}

	@GetMapping("/{id}")
	public Mono<ResponseEntity<Task>> get(@PathVariable long id) {
		return this.service.findById(id)
				.map(ResponseEntity::ok)
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}

	@PostMapping
	public Mono<Task> create(@RequestBody CreateTaskRequest request) {
		String status = (request.status() != null) ? request.status() : "TODO";
		return this.service.create(request.title(), status);
	}

	public record CreateTaskRequest(String title, String status) {
	}
}
