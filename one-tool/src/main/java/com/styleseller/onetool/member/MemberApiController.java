package com.styleseller.onetool.member;

import com.styleseller.onetool.auth.SessionUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberApiController {

    private final MemberService memberService;
    private final MemberLastViewRepository memberLastViewRepository;

    public MemberApiController(MemberService memberService, MemberLastViewRepository memberLastViewRepository) {
        this.memberService = memberService;
        this.memberLastViewRepository = memberLastViewRepository;
    }

    public record JoinRequest(String email, String password, String nickname) {}
    public record LoginRequest(String email, String password) {}
    public record UpdateProfileRequest(String nickname) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record LastViewRequest(String mode, String targetPath, String targetLabel) {}

    @PostMapping("/join")
    public Mono<Map<String, Object>> join(@RequestBody JoinRequest req) {
        return Mono.fromCallable(() -> {
            Member member = memberService.join(req.email(), req.password(), req.nickname());
            return Map.of("success", true, "email", member.getEmail(), "nickname", member.getNickname());
        });
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest req, WebSession session) {
        return Mono.fromCallable(() -> {
            Member member = memberService.login(req.email(), req.password());
            session.getAttributes().put(SessionUsers.USER_EMAIL, member.getEmail());
            return Map.of("success", true, "email", member.getEmail(), "nickname", member.getNickname());
        });
    }

    @PostMapping("/logout")
    public Mono<Map<String, Object>> logout(WebSession session) {
        return session.invalidate().then(Mono.just(Map.of("success", true)));
    }

    /**
     * 모드 선택 화면이 두 카드를 채우는 데 쓰는 목록. 최근에 본 모드가 앞에 온다.
     * 방문 기록이 없으면 빈 배열이다 — 화면이 '방문 기록 없음'으로 그린다.
     */
    @GetMapping("/me/last-views")
    public Mono<List<MemberLastView>> getLastViews(WebSession session) {
        String email = SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> memberLastViewRepository.findByMember(email));
    }

    /**
     * 마지막으로 본 위치를 기록한다. 문서를 열 때마다 화면이 알림 없이 보낸다(실패해도 화면을 막지 않는다).
     * 어느 사용자로 기록할지는 요청 본문이 아니라 세션이 정한다.
     */
    @PutMapping("/me/last-view")
    public Mono<Map<String, Object>> putLastView(@RequestBody LastViewRequest req, WebSession session) {
        String email = SessionUsers.requireLogin(session);

        if (!MemberLastViewRepository.isAllowedMode(req.mode())) {
            return Mono.error(new IllegalArgumentException("알 수 없는 모드입니다: " + req.mode()));
        }
        if (req.targetPath() == null || req.targetPath().isBlank()) {
            return Mono.error(new IllegalArgumentException("기록할 위치가 없습니다."));
        }

        return Mono.fromCallable(() -> {
            // 라벨은 화면 표시용이라 없으면 경로로 대신한다 — 기록 자체가 실패할 이유는 아니다.
            String label = (req.targetLabel() == null || req.targetLabel().isBlank())
                    ? req.targetPath()
                    : req.targetLabel();
            memberLastViewRepository.upsert(email, req.mode(), req.targetPath(), label);
            return Map.of("success", true);
        });
    }

    /**
     * 세션이 없으면 authenticated=false만 돌려준다.
     *
     * 예전에는 세션이 없을 때 기본 계정을 돌려줬는데, 그러면 로그아웃 전후 응답이 완전히 같아서
     * 화면이 바뀔 근거가 없었다(로그아웃이 동작하지 않는 것처럼 보였다).
     */
    @GetMapping("/me")
    public Mono<Map<String, Object>> me(WebSession session) {
        String email = session.getAttribute(SessionUsers.USER_EMAIL);
        if (email == null) {
            return Mono.just(Map.of("authenticated", false));
        }

        return Mono.fromCallable(() -> memberService.findByEmail(email)
                .map(member -> Map.<String, Object>of(
                        "authenticated", true,
                        "email", member.getEmail(),
                        "nickname", member.getNickname()))
                // 세션에 남은 이메일의 회원이 지워졌다면 로그인 상태로 볼 수 없다.
                .orElseGet(() -> Map.of("authenticated", false)));
    }

    /**
     * 내 닉네임을 바꾼다. 이메일은 세션의 신원이자 코멘트가 참조하는 값이라 수정 대상이 아니다.
     */
    @PatchMapping("/me")
    public Mono<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest req, WebSession session) {
        String email = SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            Member member = memberService.updateNickname(email, req.nickname());
            return Map.of("success", true, "email", member.getEmail(), "nickname", member.getNickname());
        });
    }

    /** 내 비밀번호를 바꾼다. 현재 비밀번호 확인은 서비스가 한다. */
    @PatchMapping("/me/password")
    public Mono<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest req, WebSession session) {
        String email = SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            memberService.changePassword(email, req.currentPassword(), req.newPassword());
            return Map.<String, Object>of("success", true);
        });
    }

    /**
     * 검증 실패를 400 + message로 내려준다. 프론트는 응답의 message를 그대로 노출하므로
     * 이 매핑이 없으면 "이미 존재하는 이메일입니다" 같은 실패 원인이 화면에 전달되지 않는다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }
}
