package com.styleseller.onetool.member;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 사용자가 각 모드에서 마지막으로 본 문서. 모드 선택 화면이 "이어서 보기"를 제시하는 근거다.
 */
@Repository
public class MemberLastViewRepository {

    /** 모드는 두 가지뿐이다. 화면이 보낸 값을 그대로 저장하면 오타가 그대로 행으로 남는다. */
    private static final List<String> ALLOWED_MODES = List.of("canvas", "issue");

    private final JdbcTemplate jdbcTemplate;

    public MemberLastViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MemberLastView> rowMapper = (rs, rowNum) -> new MemberLastView(
            rs.getString("mode"),
            rs.getString("target_path"),
            rs.getString("target_label"),
            rs.getString("updated_at")
    );

    public static boolean isAllowedMode(String mode) {
        return ALLOWED_MODES.contains(mode);
    }

    /**
     * (사용자, 모드)당 한 행을 유지한다.
     *
     * <p>updated_at을 갱신 절에 명시하는 이유: 같은 문서를 다시 열면 값이 모두 같아 MySQL이 행을
     * 건드리지 않고, 그러면 "언제 봤는지"가 처음 열었던 시각에 멈춘다.
     */
    public void upsert(String memberEmail, String mode, String targetPath, String targetLabel) {
        jdbcTemplate.update(
                "INSERT INTO member_last_view (member_email, mode, target_path, target_label)"
                        + " VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE"
                        + " target_path = VALUES(target_path),"
                        + " target_label = VALUES(target_label),"
                        + " updated_at = CURRENT_TIMESTAMP(3)",
                memberEmail, mode, targetPath, targetLabel
        );
    }

    /** 최근에 본 모드가 앞에 오도록 정렬한다 — 화면이 첫 행을 '최근 머문 모드'로 쓴다. */
    public List<MemberLastView> findByMember(String memberEmail) {
        return jdbcTemplate.query(
                "SELECT mode, target_path, target_label, updated_at FROM member_last_view"
                        + " WHERE member_email = ? ORDER BY updated_at DESC",
                rowMapper, memberEmail
        );
    }
}
