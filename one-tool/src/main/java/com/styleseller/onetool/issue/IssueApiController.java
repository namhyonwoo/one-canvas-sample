package com.styleseller.onetool.issue;

import com.styleseller.onetool.auth.SessionUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 이슈 보기 API. 파일을 읽기만 하므로 조회 엔드포인트만 둔다 — 이슈 문서 편집·상태 변경은 범위 밖이다.
 *
 * <p>트리는 진입 시 한 번, 본문은 클릭할 때마다 조회한다. 파일 읽기는 블로킹이라
 * {@code CanvasApiController} 와 같이 {@link Mono#fromCallable} 로 감싼다.
 */
@RestController
@RequestMapping("/api/issues")
public class IssueApiController {

    private final IssueScannerService issueScannerService;

    public IssueApiController(IssueScannerService issueScannerService) {
        this.issueScannerService = issueScannerService;
    }

    @GetMapping
    public Mono<Map<String, Object>> getIssueTree(WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            List<IssueDtos.Domain> domains = issueScannerService.scanTree();
            int epicCount = domains.stream().mapToInt(d -> d.epics().size()).sum();
            int storyCount = domains.stream()
                    .flatMap(d -> d.epics().stream())
                    .mapToInt(e -> e.stories().size())
                    .sum();
            return Map.of(
                    "domains", domains,
                    "totalEpicCount", epicCount,
                    "totalStoryCount", storyCount
            );
        });
    }

    /** @param path 애자일 이슈 루트 기준 상대 경로 (예: partner-review/epic-admin-partner-review-detail) */
    @GetMapping("/epic")
    public Mono<IssueDtos.Epic> getEpic(@RequestParam("path") String path, WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> issueScannerService.readEpic(path));
    }

    /** @param path 애자일 이슈 루트 기준 상대 경로 (예: partner-review/epic-x/story-y) */
    @GetMapping("/story")
    public Mono<IssueDtos.Story> getStory(@RequestParam("path") String path, WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> issueScannerService.readStory(path));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }
}
