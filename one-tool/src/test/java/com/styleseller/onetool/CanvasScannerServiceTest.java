package com.styleseller.onetool;

import com.styleseller.onetool.canvas.CanvasFileDto;
import com.styleseller.onetool.canvas.CanvasFolderDto;
import com.styleseller.onetool.canvas.CanvasScannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스캐너 테스트. 실제 레포를 읽으면 캔버스가 늘거나 줄 때마다 깨지므로 임시 디렉터리에 같은
 * 구조를 만들어 검증한다(IssueScannerServiceTest와 같은 방식). 스프링 컨텍스트·DB를 쓰지 않는다.
 */
class CanvasScannerServiceTest {

    @TempDir
    Path root;

    private CanvasScannerService service;

    @BeforeEach
    void setUp() throws IOException {
        // 확정 캔버스 — meta 3종이 <title> 바로 다음에 있다.
        writeCanvas(root.resolve("partner-review"), "canvas-partner-business-info.html", """
                <title>통합캔버스 — 비즈니스 정보 등록·수정</title>
                <meta name="canvas-status" content="confirmed">
                <meta name="canvas-confirmed-at" content="2026-08-04T17:34:52+09:00">
                <meta name="canvas-confirmed-issue" content="agile-issue/partner-review/epic-partner-business-info">
                """);

        // 미확정 캔버스 — 확정 meta는 없지만 수정일은 모든 캔버스가 갖는다.
        writeCanvas(root.resolve("partner-review"), "canvas-partner-draft.html", """
                <title>통합캔버스 — 아직 논의 중</title>
                <meta name="canvas-updated-at" content="2026-08-11T09:05:00+09:00">
                """);

        // 확정 이후 캔버스를 고친 경우.
        writeCanvas(root.resolve("order-cancel"), "canvas-app-order-cancel.html", """
                <title>통합캔버스 — 주문 취소</title>
                <meta content="confirmed" name="canvas-status">
                <meta name="canvas-confirmed-at" content="2026-08-04T17:34:52+09:00">
                <meta name="canvas-confirmed-issue" content="agile-issue/order/epic-order-cancel">
                <meta name="canvas-updated-at" content="2026-08-10T11:20:00+09:00">
                """);

        // 규칙 예시 전용 폴더는 목록에 넣지 않는다.
        writeCanvas(root.resolve("sample"), "canvas-sample.html", "<title>샘플</title>\n");

        service = new CanvasScannerService(root.toString());
    }

    @Test
    void 캔버스_폴더를_훑고_sample은_제외한다() {
        List<String> names = service.scanFolders().stream().map(CanvasFolderDto::name).toList();

        assertThat(names).containsExactly("order-cancel", "partner-review");
    }

    @Test
    void 확정_meta를_읽어_확정_정보를_채운다() {
        CanvasFileDto canvas = find("partner-review", "canvas-partner-business-info.html");

        assertThat(canvas.title()).isEqualTo("비즈니스 정보 등록·수정");
        assertThat(canvas.confirmed()).isTrue();
        assertThat(canvas.confirmedAt()).isEqualTo("2026-08-04T17:34:52+09:00");
        assertThat(canvas.confirmedIssue()).isEqualTo("agile-issue/partner-review/epic-partner-business-info");
        // 수정일이 없는 캔버스도 있다 — 확정 직후라 아직 고친 적이 없는 경우.
        assertThat(canvas.updatedAt()).isNull();
    }

    @Test
    void 확정_meta가_없으면_미확정이지만_수정일은_읽는다() {
        CanvasFileDto canvas = find("partner-review", "canvas-partner-draft.html");

        assertThat(canvas.confirmed()).isFalse();
        assertThat(canvas.confirmedAt()).isNull();
        assertThat(canvas.confirmedIssue()).isNull();
        // 수정일은 확정과 무관한 값이라 미확정 캔버스에서도 그대로 내려간다.
        assertThat(canvas.updatedAt()).isEqualTo("2026-08-11T09:05:00+09:00");
    }

    /** 속성 순서를 강제하지 않는다 — 이 캔버스는 content가 name보다 앞에 있다. */
    @Test
    void 확정_이후_수정_시각을_함께_읽는다() {
        CanvasFileDto canvas = find("order-cancel", "canvas-app-order-cancel.html");

        assertThat(canvas.confirmed()).isTrue();
        assertThat(canvas.updatedAt()).isEqualTo("2026-08-10T11:20:00+09:00");
    }

    @Test
    void 확정_meta_값이_깨져도_목록이_죽지_않는다() throws IOException {
        // 상태 값이 규칙에 없는 값이면 미확정으로 본다.
        writeCanvas(root.resolve("broken"), "canvas-app-status.html", """
                <title>통합캔버스 — 상태값 오타</title>
                <meta name="canvas-status" content="confirm">
                <meta name="canvas-confirmed-at" content="2026-08-04T17:34:52+09:00">
                """);
        // 상태는 맞고 시각만 깨진 경우 — 확정은 사실이므로 유지하고, 못 읽는 시각만 버린다.
        writeCanvas(root.resolve("broken"), "canvas-app-time.html", """
                <title>통합캔버스 — 시각 오타</title>
                <meta name="canvas-status" content="confirmed">
                <meta name="canvas-confirmed-at" content="2026년 8월 4일">
                <meta name="canvas-confirmed-issue" content="agile-issue/order/epic-x">
                """);
        service = new CanvasScannerService(root.toString());

        CanvasFileDto badStatus = find("broken", "canvas-app-status.html");
        assertThat(badStatus.confirmed()).isFalse();
        assertThat(badStatus.confirmedAt()).isNull();

        CanvasFileDto badTime = find("broken", "canvas-app-time.html");
        assertThat(badTime.confirmed()).isTrue();
        assertThat(badTime.confirmedAt()).isNull();
        assertThat(badTime.confirmedIssue()).isEqualTo("agile-issue/order/epic-x");
    }

    /** 규칙이 meta를 &lt;title&gt; 바로 다음에 두라고 하는 이유 — 앞머리 스캔 범위를 벗어나면 못 읽는다. */
    @Test
    void 앞머리를_벗어난_meta는_읽지_못한다() throws IOException {
        writeCanvas(root.resolve("late-meta"), "canvas-app-late.html",
                "<title>통합캔버스 — 늦은 meta</title>\n"
                        + "<style>" + "/*".repeat(1) + " ".repeat(40000) + "*/</style>\n"
                        + "<meta name=\"canvas-status\" content=\"confirmed\">\n");
        service = new CanvasScannerService(root.toString());

        assertThat(find("late-meta", "canvas-app-late.html").confirmed()).isFalse();
    }

    private CanvasFileDto find(String folder, String file) {
        return service.scanFolders().stream()
                .filter(f -> f.name().equals(folder))
                .flatMap(f -> f.canvases().stream())
                .filter(c -> c.name().equals(file))
                .findFirst()
                .orElseThrow(() -> new AssertionError(folder + "/" + file + " 를 찾지 못했다"));
    }

    private void writeCanvas(Path dir, String file, String head) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(file), """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                %s</head>
                <body><h1>캔버스</h1></body>
                </html>
                """.formatted(head));
    }
}
