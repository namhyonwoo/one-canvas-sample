package com.styleseller.onetool.comment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CommentRepository {

    private static final String INSERT = "INSERT INTO comment"
            + " (canvas_path, block_name, author_id, body, resolved, parent_id, anchor_id, anchor_path, anchor_hash)"
            + " VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?)";

    /**
     * 화면에 이메일 대신 닉네임을 보여주기 위해 member를 함께 읽는다.
     * 닉네임을 comment에 복사해두지 않는 이유는 {@link Comment#getAuthorNickname()} 참고.
     * 미등록/삭제된 계정의 코멘트도 사라지면 안 되므로 LEFT JOIN이다(닉네임만 null이 된다).
     */
    private static final String SELECT_BASE = "SELECT c.*, m.nickname AS author_nickname"
            + " FROM comment c LEFT JOIN member m ON m.email = c.author_id";

    private final JdbcTemplate jdbcTemplate;
    /**
     * 삭제는 [답글 삭제 → 코멘트 삭제] 두 문장이라 하나로 묶어야 한다. 중간에 끊기면
     * 남의 답글만 사라지고 코멘트는 남는다. @Transactional(AOP 프록시) 대신 명시적으로 쓴다.
     */
    private final TransactionTemplate transactionTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private final RowMapper<Comment> rowMapper = (rs, rowNum) -> {
        // parent_id는 nullable이다. getLong은 NULL을 0으로 돌려주므로 wasNull로 최상위 코멘트와 구분한다.
        long rawParentId = rs.getLong("parent_id");
        Long parentId = rs.wasNull() ? null : rawParentId;

        Comment comment = new Comment(
                rs.getLong("id"),
                rs.getString("canvas_path"),
                rs.getString("block_name"),
                rs.getString("author_id"),
                rs.getString("body"),
                rs.getInt("resolved") == 1,
                parentId,
                rs.getString("created_at")
        );
        comment.setAnchor(rs.getString("anchor_id"), rs.getString("anchor_path"), rs.getString("anchor_hash"));
        comment.setAuthorNickname(rs.getString("author_nickname"));
        return comment;
    };

    /**
     * INSERT 후 생성된 키를 {@link KeyHolder}로 받는다.
     * 생성 키를 별도 질의로 다시 읽으면 커넥션을 다시 빌리는 사이에 다른 스레드의
     * INSERT가 끼어들어 남의 id를 읽을 수 있다.
     */
    public Comment save(Comment comment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, comment.getCanvasPath());
            ps.setString(2, comment.getBlockName());
            ps.setString(3, comment.getAuthorId());
            ps.setString(4, comment.getBody());
            // parent_id는 nullable이다. setLong은 null을 0으로 넣어 FK 위반을 만든다.
            ps.setObject(5, comment.getParentId(), Types.BIGINT);
            ps.setString(6, comment.getAnchorId());
            ps.setString(7, comment.getAnchorPath());
            ps.setString(8, comment.getAnchorHash());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("INSERT 후 생성된 키를 얻지 못했습니다.");
        }
        return findById(key.longValue());
    }

    public Comment findById(Long id) {
        return jdbcTemplate.queryForObject(SELECT_BASE + " WHERE c.id = ?", rowMapper, id);
    }

    /** 답글의 부모 검증처럼 존재하지 않을 수 있는 조회에 사용한다. */
    public Comment findByIdOrNull(Long id) {
        List<Comment> found = jdbcTemplate.query(SELECT_BASE + " WHERE c.id = ?", rowMapper, id);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Comment> findByCanvasPath(String canvasPath) {
        return jdbcTemplate.query(
                SELECT_BASE + " WHERE c.canvas_path = ? ORDER BY c.id ASC",
                rowMapper, canvasPath
        );
    }

    /**
     * 캔버스별 미해결 코멘트 수. 사이드바에서 어느 캔버스에 볼 것이 남았는지 보여주기 위한 집계다.
     * 답글은 부모의 해결 상태를 따르므로(개별 resolve가 없다) 최상위 코멘트만 센다.
     * 미해결이 0인 캔버스는 결과에 아예 없으므로 호출부가 0으로 취급해야 한다.
     */
    public Map<String, Integer> countUnresolvedByCanvasPath() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT canvas_path, COUNT(*) AS cnt FROM comment"
                        + " WHERE resolved = 0 AND parent_id IS NULL"
                        + " GROUP BY canvas_path"
        );

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put((String) row.get("canvas_path"), ((Number) row.get("cnt")).intValue());
        }
        return counts;
    }

    /**
     * 코멘트를 답글까지 함께 지운다.
     *
     * 답글이 parent_id로 코멘트를 참조(FK)하므로 자식을 먼저 지워야 한다. 그래서 코멘트를 지우면
     * 거기 달린 남의 답글도 함께 사라진다 — 호출부가 사용자에게 먼저 알려야 한다.
     *
     * @return 지워진 행 수(코멘트 + 답글). 0이면 이미 없는 코멘트다.
     */
    public int deleteWithReplies(Long id) {
        Integer deleted = transactionTemplate.execute(status -> {
            int replies = jdbcTemplate.update("DELETE FROM comment WHERE parent_id = ?", id);
            int self = jdbcTemplate.update("DELETE FROM comment WHERE id = ?", id);
            return replies + self;
        });
        return deleted == null ? 0 : deleted;
    }

    public Comment resolve(Long id, boolean resolved) {
        jdbcTemplate.update("UPDATE comment SET resolved = ? WHERE id = ?", resolved ? 1 : 0, id);
        return findById(id);
    }
}
