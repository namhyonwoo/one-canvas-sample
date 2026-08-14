package com.styleseller.onetool.comment;

import com.styleseller.onetool.auth.SessionUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentApiController {

    private final CommentRepository commentRepository;

    public CommentApiController(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    /**
     * parentId가 있으면 답글로 등록된다. 이때 canvasPath/blockName/앵커는 부모 값을 따른다.
     * anchor* 는 캔버스 원본을 수정하지 않고 블록을 다시 찾기 위한 값으로, 클라이언트가 렌더 시점에 계산해 보낸다.
     */
    public record CreateCommentRequest(String canvasPath, String blockName, String body, Long parentId,
                                       String anchorId, String anchorPath, String anchorHash) {}
    public record ResolveRequest(boolean resolved) {}

    // 조회도 로그인을 요구한다. 코멘트 본문은 캔버스 내용만큼이나 내부 정보이고,
    // 화면만 막으면 URL을 직접 열었을 때 그대로 노출된다.
    @GetMapping
    public Mono<List<Comment>> getComments(@RequestParam("canvasPath") String canvasPath, WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> commentRepository.findByCanvasPath(canvasPath));
    }

    /**
     * 사이드바 캔버스 목록에 미해결 수를 붙이기 위한 집계. 캔버스마다 목록 API를 부르지 않기 위해
     * 한 번에 내려준다. 미해결이 없는 캔버스는 키 자체가 없다.
     */
    @GetMapping("/unresolved-counts")
    public Mono<Map<String, Integer>> getUnresolvedCounts(WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(commentRepository::countUnresolvedByCanvasPath);
    }

    @PostMapping
    public Mono<Map<String, Object>> createComment(@RequestBody CreateCommentRequest req, WebSession session) {
        // 예전에는 세션이 없으면 기본 계정으로 작성됐다. 그래서 로그아웃한 뒤에도 남의 이름으로
        // 코멘트가 달렸다. 작성은 로그인한 세션에서만 허용한다.
        String authorId = SessionUsers.requireLogin(session);
        if (req.body() == null || req.body().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("내용을 입력해주세요"));
        }
        if (req.body().length() > 1000) {
            return Mono.error(new IllegalArgumentException("코멘트는 최대 1,000자까지 입력 가능합니다."));
        }

        return Mono.fromCallable(() -> {
            String canvasPath = req.canvasPath();
            String blockName = req.blockName();
            String anchorId = req.anchorId();
            String anchorPath = req.anchorPath();
            String anchorHash = req.anchorHash();

            if (req.parentId() != null) {
                Comment parent = commentRepository.findByIdOrNull(req.parentId());
                if (parent == null) {
                    throw new IllegalArgumentException("답글을 달 코멘트를 찾을 수 없습니다.");
                }
                // 답글은 한 뎁스만 허용한다.
                if (parent.getParentId() != null) {
                    throw new IllegalArgumentException("답글에는 답글을 달 수 없습니다.");
                }
                // 목록은 canvasPath로 조회하므로, 부모와 다른 위치에 저장되면 답글이 화면에서 사라진다.
                canvasPath = parent.getCanvasPath();
                blockName = parent.getBlockName();
                // 답글은 부모와 같은 블록에 붙는다. 앵커도 부모를 그대로 따라야 폴백 결과가 갈리지 않는다.
                anchorId = parent.getAnchorId();
                anchorPath = parent.getAnchorPath();
                anchorHash = parent.getAnchorHash();
            }

            Comment comment = new Comment(canvasPath, blockName, authorId, req.body().trim(), req.parentId());
            comment.setAnchor(anchorId, anchorPath, anchorHash);
            Comment saved = commentRepository.save(comment);
            return Map.of("success", true, "comment", saved);
        });
    }

    /** 로그인은 했지만 남의 코멘트를 건드리려 할 때 던진다. */
    static class NotOwnerException extends RuntimeException {
        NotOwnerException(String message) {
            super(message);
        }
    }

    /**
     * 코멘트를 지운다. 작성자 본인만 지울 수 있다.
     * 답글이 코멘트를 FK로 참조하므로 답글도 함께 사라진다 — 프론트가 미리 확인을 받는다.
     */
    @DeleteMapping("/{id}")
    public Mono<Map<String, Object>> deleteComment(@PathVariable Long id, WebSession session) {
        String userEmail = SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            Comment comment = commentRepository.findByIdOrNull(id);
            if (comment == null) {
                throw new IllegalArgumentException("이미 삭제된 코멘트입니다.");
            }
            // 권한 판단 기준은 화면이 보내온 값이 아니라 세션의 사용자다.
            if (!userEmail.equals(comment.getAuthorId())) {
                throw new NotOwnerException("본인이 작성한 코멘트만 삭제할 수 있습니다.");
            }
            commentRepository.deleteWithReplies(id);
            return Map.of("success", true);
        });
    }

    /**
     * 검증 실패를 400 + message로 내려준다. 프론트가 응답의 message를 그대로 노출하므로
     * 이 매핑이 없으면 실패 원인이 화면에 전달되지 않는다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }

    /** 로그인 문제가 아니므로 401이 아니다 — 로그인 창을 띄워도 해결되지 않는다. */
    @ExceptionHandler(NotOwnerException.class)
    public ResponseEntity<Map<String, Object>> handleNotOwner(NotOwnerException e) {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
    }

    @PatchMapping("/{id}/resolve")
    public Mono<Map<String, Object>> resolveComment(@PathVariable Long id, @RequestBody ResolveRequest req,
                                                   WebSession session) {
        // 해결 처리도 코멘트 상태를 바꾸는 쓰기다. 작성과 같은 기준으로 로그인을 요구한다.
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            Comment updated = commentRepository.resolve(id, req.resolved());
            return Map.of("success", true, "comment", updated);
        });
    }
}
