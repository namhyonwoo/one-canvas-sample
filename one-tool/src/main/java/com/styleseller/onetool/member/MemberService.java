package com.styleseller.onetool.member;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
        initDefaultUser();
    }

    private void initDefaultUser() {
        if (memberRepository.findByEmail("kuyanam@styleseller.co.kr").isEmpty()) {
            memberRepository.save(new Member("kuyanam@styleseller.co.kr", "1234", "Kuyanam"));
        }
    }

    public Member join(String email, String password, String nickname) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + email);
        }
        Member member = new Member(email, password, nickname);
        return memberRepository.save(member);
    }

    public Member login(String email, String password) {
        Optional<Member> memberOpt = memberRepository.findByEmail(email);
        if (memberOpt.isEmpty() || !memberOpt.get().getPassword().equals(password)) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return memberOpt.get();
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    /** member.nickname은 VARCHAR(100)이다. 넘기면 DB가 잘라내거나 던지므로 여기서 먼저 막는다. */
    private static final int NICKNAME_MAX_LENGTH = 100;
    private static final int PASSWORD_MAX_LENGTH = 255;

    /**
     * 닉네임을 바꾼다. 코멘트는 닉네임을 복사해두지 않고 조회 시 member에서 읽으므로,
     * 여기서 바꾸면 이전에 쓴 코멘트의 작성자 이름까지 함께 바뀐다.
     */
    public Member updateNickname(String email, String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요.");
        }
        if (trimmed.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("닉네임은 최대 " + NICKNAME_MAX_LENGTH + "자까지 입력 가능합니다.");
        }
        return memberRepository.updateNickname(email, trimmed)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    /**
     * 비밀번호를 바꾼다. 세션만으로는 자리를 비운 사이 남이 바꾸는 것을 막지 못하므로
     * 현재 비밀번호를 다시 확인한다.
     *
     * 저장은 평문이다 — 기존 가입/로그인이 평문 비교라 여기서만 해싱하면 로그인이 깨진다.
     */
    public Member changePassword(String email, String currentPassword, String newPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        if (currentPassword == null || !member.getPassword().equals(currentPassword)) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호를 입력해주세요.");
        }
        if (newPassword.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 최대 " + PASSWORD_MAX_LENGTH + "자까지 입력 가능합니다.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 비밀번호를 입력해주세요.");
        }

        return memberRepository.updatePassword(email, newPassword)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }
}
