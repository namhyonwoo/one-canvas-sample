package com.styleseller.onetool.task;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * MySQL 접근 계층. 여기의 모든 메서드는 <b>블로킹</b>이다.
 * 리액티브 스트림 위에서 호출할 때는 반드시 {@link TaskService}를 거쳐
 * boundedElastic 스케줄러로 오프로딩해야 한다.
 */
@Repository
public class TaskRepository {

	private static final String SELECT_ALL = """
			SELECT id, title, status, created_at FROM task ORDER BY id DESC
			""";

	private static final String SELECT_BY_ID = """
			SELECT id, title, status, created_at FROM task WHERE id = :id
			""";

	private static final String INSERT = """
			INSERT INTO task (title, status) VALUES (:title, :status)
			""";

	private final JdbcClient jdbcClient;

	public TaskRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<Task> findAll() {
		return this.jdbcClient.sql(SELECT_ALL)
				.query(Task.class)
				.list();
	}

	public Optional<Task> findById(long id) {
		return this.jdbcClient.sql(SELECT_BY_ID)
				.param("id", id)
				.query(Task.class)
				.optional();
	}

	/**
	 * INSERT 후 생성된 키를 {@link KeyHolder}로 받는다.
	 * 생성 키를 별도 질의로 다시 읽으면 커넥션을 다시 빌리는 사이에 다른 스레드의
	 * INSERT가 끼어들어 남의 id를 읽을 수 있다.
	 */
	public Task create(String title, String status) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.jdbcClient.sql(INSERT)
				.param("title", title)
				.param("status", status)
				.update(keyHolder);
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("INSERT 후 생성된 키를 얻지 못했습니다.");
		}
		return findById(key.longValue()).orElseThrow();
	}
}
