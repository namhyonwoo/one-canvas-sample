package com.styleseller.onetool.issue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스캐너 테스트. 실제 레포를 읽으면 이슈 문서가 늘거나 줄 때마다 깨지므로 임시 디렉터리에
 * 같은 구조를 만들어 검증한다. 스프링 컨텍스트·DB를 쓰지 않는다.
 */
class IssueScannerServiceTest {

    @TempDir
    Path root;

    private IssueScannerService service;

    @BeforeEach
    void setUp() throws IOException {
        // 실무 도메인
        writeIndex(root.resolve("partner-review"), """
                ---
                updated: 2026-08-05
                ---

                # 도메인: partner-review (파트너 심사)
                """);
        writeIndex(root.resolve("partner-review/epic-admin-partner-review-detail"), """
                ---
                updated: 2026-08-05
                ---

                # 에픽: admin-partner-review-detail (총괄 파트너 심사 상세)

                ## 목적
                승인/반려를 처리한다.
                """);
        writeIndex(root.resolve("partner-review/epic-admin-partner-review-detail/story-admin-detail-history"), """
                ---
                status: in-progress
                updated: 2026-08-05
                ---

                # 유저 스토리: admin-detail-history (과거 이력을 확인한다)

                ## User Story
                과거 이력을 읽기 전용으로 열람한다.
                """);
        // index.md가 없는 스토리 — 트리 전체가 실패하지 않아야 한다.
        Files.createDirectories(root.resolve("partner-review/epic-admin-partner-review-detail/story-no-index"));

        // 규칙 예시 전용 도메인 — 트리에서 제외돼야 한다.
        writeIndex(root.resolve("sample/settlement/epic-partner-settlement-list"), """
                # 에픽: partner-settlement-list (정산 목록)
                """);

        // 에픽이 없는 디렉터리 — 도메인이 아니므로 제외돼야 한다.
        Files.createDirectories(root.resolve("api-doc"));
        Files.writeString(root.resolve("api-doc/spec.json"), "{}");

        service = new IssueScannerService(root.toString());
    }

    private void writeIndex(Path dir, String markdown) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.md"), markdown);
    }

    @Test
    void 트리는_도메인_에픽_스토리_3계층으로_내려간다() {
        List<IssueDtos.Domain> domains = service.scanTree();

        assertThat(domains).hasSize(1);
        IssueDtos.Domain domain = domains.get(0);
        assertThat(domain.name()).isEqualTo("partner-review");
        assertThat(domain.title()).isEqualTo("파트너 심사");
        assertThat(domain.epics()).hasSize(1);
        assertThat(domain.epics().get(0).title()).isEqualTo("총괄 파트너 심사 상세");
        assertThat(domain.epics().get(0).stories())
                .extracting(IssueDtos.StorySummary::name)
                .containsExactly("story-admin-detail-history", "story-no-index");
    }

    @Test
    void 스토리_상태는_문서_frontmatter를_그대로_옮긴다() {
        IssueDtos.StorySummary story = service.scanTree().get(0).epics().get(0).stories().get(0);
        assertThat(story.status()).isEqualTo("in-progress");
        assertThat(story.title()).isEqualTo("과거 이력을 확인한다");
    }

    @Test
    void 규칙_예시용_sample_도메인은_트리에_노출하지_않는다() {
        assertThat(service.scanTree()).extracting(IssueDtos.Domain::name).doesNotContain("sample");
    }

    @Test
    void 에픽이_없는_디렉터리는_도메인으로_보지_않는다() {
        assertThat(service.scanTree()).extracting(IssueDtos.Domain::name).doesNotContain("api-doc");
    }

    @Test
    void index가_없는_스토리도_트리에서_빠지지_않는다() {
        IssueDtos.StorySummary story = service.scanTree().get(0).epics().get(0).stories().get(1);
        assertThat(story.name()).isEqualTo("story-no-index");
        assertThat(story.title()).isNull();
        assertThat(story.status()).isNull();
    }

    @Test
    void 에픽_본문을_경로로_읽는다() {
        IssueDtos.Epic epic = service.readEpic("partner-review/epic-admin-partner-review-detail");
        assertThat(epic.title()).isEqualTo("총괄 파트너 심사 상세");
        assertThat(epic.purpose()).isEqualTo("승인/반려를 처리한다.");
    }

    @Test
    void 스토리_본문을_경로로_읽는다() {
        IssueDtos.Story story = service.readStory(
                "partner-review/epic-admin-partner-review-detail/story-admin-detail-history");
        assertThat(story.status()).isEqualTo("in-progress");
        assertThat(story.description()).isEqualTo("과거 이력을 읽기 전용으로 열람한다.");
    }

    @Test
    void 루트_밖_경로는_거부한다() {
        assertThatThrownBy(() -> service.readEpic("../../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문서가_없는_경로는_거부한다() {
        assertThatThrownBy(() -> service.readStory("partner-review/epic-admin-partner-review-detail/story-no-index"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이슈 문서를 찾을 수 없습니다");
    }
}
