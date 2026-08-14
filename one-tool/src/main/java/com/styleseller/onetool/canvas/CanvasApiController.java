package com.styleseller.onetool.canvas;

import com.styleseller.onetool.auth.SessionUsers;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/canvases")
public class CanvasApiController {

    private final CanvasScannerService canvasScannerService;

    public CanvasApiController(CanvasScannerService canvasScannerService) {
        this.canvasScannerService = canvasScannerService;
    }

    @GetMapping
    public Mono<Map<String, Object>> getCanvases(WebSession session) {
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> {
            List<CanvasFolderDto> folders = canvasScannerService.scanFolders();
            int totalCount = folders.stream().mapToInt(f -> f.canvases().size()).sum();
            return Map.of(
                    "folders", folders,
                    "totalCanvasCount", totalCount
            );
        });
    }

    @GetMapping(value = "/content", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> getCanvasContent(@RequestParam("path") String path, WebSession session) {
        // iframe이 부르는 경로다. 같은 출처라 세션 쿠키가 함께 실린다.
        SessionUsers.requireLogin(session);
        return Mono.fromCallable(() -> canvasScannerService.readCanvasContent(path));
    }
}
