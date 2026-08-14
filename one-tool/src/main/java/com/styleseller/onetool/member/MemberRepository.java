package com.styleseller.onetool.member;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Member> rowMapper = (rs, rowNum) -> new Member(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getString("nickname"),
            rs.getString("created_at")
    );

    public Member save(Member member) {
        jdbcTemplate.update(
                "INSERT INTO member (email, password, nickname) VALUES (?, ?, ?)",
                member.getEmail(), member.getPassword(), member.getNickname()
        );
        return findByEmail(member.getEmail()).orElse(member);
    }

    public Optional<Member> findByEmail(String email) {
        List<Member> results = jdbcTemplate.query("SELECT * FROM member WHERE email = ?", rowMapper, email);
        return results.stream().findFirst();
    }

    /**
     * 닉네임만 바꾼다. 이메일은 세션의 신원이자 코멘트(author_id)가 참조하는 값이라 여기서 건드리지 않는다.
     *
     * @return 갱신된 회원. 이메일에 해당하는 회원이 없으면 비어 있다.
     */
    public Optional<Member> updateNickname(String email, String nickname) {
        jdbcTemplate.update("UPDATE member SET nickname = ? WHERE email = ?", nickname, email);
        return findByEmail(email);
    }

    public Optional<Member> updatePassword(String email, String password) {
        jdbcTemplate.update("UPDATE member SET password = ? WHERE email = ?", password, email);
        return findByEmail(email);
    }

    public List<Member> findAll() {
        return jdbcTemplate.query("SELECT * FROM member ORDER BY id ASC", rowMapper);
    }
}
