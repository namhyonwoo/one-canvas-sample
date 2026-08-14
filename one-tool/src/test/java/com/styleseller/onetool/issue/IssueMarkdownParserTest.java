package com.styleseller.onetool.issue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 문서 파서 테스트. 실제 문서(agile-issue/partner-review)의 형식을 그대로 축약해 쓴다.
 * DB·스프링 컨텍스트를 쓰지 않는 순수 단위 테스트다.
 */
class IssueMarkdownParserTest {

    private static final String EPIC_MD = """
            ---
            updated: 2026-08-05
            ---

            # 에픽: admin-partner-review-detail (총괄 파트너 심사 상세·심사하기)

            > 캔버스: one-canvas/partner-review/canvas-admin-partner-review-detail.html

            ## 목적 (비즈니스 목표)
            심사자가 제출 정보를 검토하고 승인/반려를 처리한다.

            ## 주요 범위 (In-Scope)
            여기는 이슈 보기 범위가 아니다.
            """;

    private static final String STORY_MD = """
            ---
            status: in-progress
            updated: 2026-08-05
            ---

            # 유저 스토리: admin-detail-history (심사자가 과거 심사 이력을 그 시점 그대로 확인한다)

            ## User Story
            총괄 심사자가 재심사 판단 근거를 확인하기 위해 과거 이력을 읽기 전용으로 열람한다.

            > 기획 출처: `epic-admin-partner-review-detail` 참조

            ## 수락 기준 (Acceptance Criteria)
            > Given-When-Then 시나리오로 작성한다.

            - [ ] **AC1** Given 반려 이력이 있는 건에서, When 과거 항목을 클릭하면, Then 스냅샷이 표시된다.
            - [x] **AC2** (보강) Given 임시등록 상태이고, When 상세를 열면, Then 카드가 표시되지 않는다.
            - [ ] ~~AC7~~ [폐기됨,2026-08-01 10:00] 더 이상 필요 없어진 기준

            ## 태스크 (개발자 Role)

            ### Backend
            - [ ] [완료,2026-08-04 21:08] 심사 이력 목록 제공 (AC1)

            ### Frontend
            - [ ] [생성됨,2026-08-05 09:00] 히스토리 카드 목록 구현 (AC1) [ui: 화면1 ⑧ display/reviewHistoryCard]

            ## 테스트 (QA Role)
            ### 성공
            - 과거 항목 클릭 → 스냅샷이 보인다. (AC1)
            ### 실패 (예외처리 포함)
            - 임시등록 건 → 카드가 아예 없다. (AC2)

            ## 구현 참조
            - PR: -
            """;

    @Test
    void 에픽은_제목과_목적만_파싱한다() {
        IssueDtos.Epic epic = IssueMarkdownParser.parseEpic("epic-admin-partner-review-detail", EPIC_MD);

        assertThat(epic.name()).isEqualTo("epic-admin-partner-review-detail");
        assertThat(epic.title()).isEqualTo("총괄 파트너 심사 상세·심사하기");
        assertThat(epic.purpose()).isEqualTo("심사자가 제출 정보를 검토하고 승인/반려를 처리한다.");
    }

    @Test
    void 에픽_목적에_다음_섹션_내용이_섞이지_않는다() {
        IssueDtos.Epic epic = IssueMarkdownParser.parseEpic("epic-x", EPIC_MD);
        assertThat(epic.purpose()).doesNotContain("이슈 보기 범위가 아니다");
    }

    @Test
    void 스토리는_제목_상태_설명을_파싱한다() {
        IssueDtos.Story story = IssueMarkdownParser.parseStory("story-admin-detail-history", STORY_MD);

        assertThat(story.title()).isEqualTo("심사자가 과거 심사 이력을 그 시점 그대로 확인한다");
        assertThat(story.status()).isEqualTo("in-progress");
        assertThat(story.updated()).isEqualTo("2026-08-05");
        assertThat(story.description()).startsWith("총괄 심사자가 재심사 판단 근거를");
        // 인용(>)은 규칙 안내문이라 본문으로 옮기지 않는다.
        assertThat(story.description()).doesNotContain("기획 출처");
    }

    @Test
    void 수락기준은_문서의_번호와_체크상태를_그대로_유지한다() {
        IssueDtos.Story story = IssueMarkdownParser.parseStory("story-x", STORY_MD);

        assertThat(story.acceptanceCriteria()).hasSize(3);
        assertThat(story.acceptanceCriteria().get(0).no()).isEqualTo("AC1");
        assertThat(story.acceptanceCriteria().get(0).done()).isFalse();
        assertThat(story.acceptanceCriteria().get(0).text()).startsWith("Given 반려 이력이");
        assertThat(story.acceptanceCriteria().get(1).no()).isEqualTo("AC2");
        assertThat(story.acceptanceCriteria().get(1).done()).isTrue();
        // 폐기된 AC도 번호 자리를 유지한다(번호는 재사용되지 않는다).
        assertThat(story.acceptanceCriteria().get(2).no()).isEqualTo("~~AC7~~");
    }

    @Test
    void 태스크는_Role별로_묶이고_상태_마커가_분리된다() {
        IssueDtos.Story story = IssueMarkdownParser.parseStory("story-x", STORY_MD);

        assertThat(story.taskGroups()).hasSize(2);
        assertThat(story.taskGroups().get(0).role()).isEqualTo("Backend");
        IssueDtos.Task backend = story.taskGroups().get(0).tasks().get(0);
        assertThat(backend.marker()).isEqualTo("완료");
        assertThat(backend.markerAt()).isEqualTo("2026-08-04 21:08");
        assertThat(backend.text()).isEqualTo("심사 이력 목록 제공 (AC1)");

        IssueDtos.Task frontend = story.taskGroups().get(1).tasks().get(0);
        assertThat(story.taskGroups().get(1).role()).isEqualTo("Frontend");
        assertThat(frontend.marker()).isEqualTo("생성됨");
        assertThat(frontend.text()).contains("[ui: 화면1 ⑧ display/reviewHistoryCard]");
    }

    @Test
    void 테스트는_성공과_실패_구분을_유지한다() {
        IssueDtos.Story story = IssueMarkdownParser.parseStory("story-x", STORY_MD);

        assertThat(story.tests().success()).containsExactly("과거 항목 클릭 → 스냅샷이 보인다. (AC1)");
        assertThat(story.tests().failure()).containsExactly("임시등록 건 → 카드가 아예 없다. (AC2)");
    }

    @Test
    void 문서에_없는_섹션은_지어내지_않고_비워둔다() {
        String bare = """
                ---
                status: draft
                ---

                # 유저 스토리: bare-story (아직 명세만 있는 스토리)

                ## User Story
                설명만 있는 스토리다.
                """;

        IssueDtos.Story story = IssueMarkdownParser.parseStory("story-bare", bare);

        assertThat(story.status()).isEqualTo("draft");
        assertThat(story.updated()).isNull();
        assertThat(story.acceptanceCriteria()).isEmpty();
        assertThat(story.taskGroups()).isEmpty();
        assertThat(story.tests().success()).isEmpty();
        assertThat(story.tests().failure()).isEmpty();
    }

    @Test
    void H1에_한글명이_없으면_영문명을_그대로_쓴다() {
        String noKorean = """
                # 에픽: epic-without-korean-name
                ## 목적
                한글명 병기가 없는 문서.
                """;

        assertThat(IssueMarkdownParser.parseEpic("epic-x", noKorean).title())
                .isEqualTo("epic-without-korean-name");
    }
}
