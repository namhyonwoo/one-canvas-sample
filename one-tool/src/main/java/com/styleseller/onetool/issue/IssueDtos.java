package com.styleseller.onetool.issue;

import java.util.List;

/**
 * 이슈 보기가 주고받는 DTO 모음.
 *
 * <p>애자일 이슈는 <code>&lt;도메인&gt;/epic-*∕story-*∕index.md</code> 로 관리되는 파일이고
 * (agile-issue/agile-issue-rule.md 「디렉토리 구조와 작성 원칙」), 이 파일들을 읽어 그대로 화면에
 * 옮기는 것이 이슈 보기다. 따라서 DTO도 문서에 있는 항목만 담고 새 필드를 만들지 않는다.
 *
 * <p>문서에 없는 항목은 지어내지 않고 null(또는 빈 목록)로 내려보내며, 무엇을 표시할지는 화면이 정한다.
 */
public final class IssueDtos {

    private IssueDtos() {
    }

    /** 트리 1계층 — 도메인. */
    public record Domain(String name, String title, List<EpicSummary> epics) {
    }

    /** 트리 2계층 — 에픽. 트리에 필요한 최소 정보만 담는다(본문은 별도 조회). */
    public record EpicSummary(String name, String title, List<StorySummary> stories) {
    }

    /** 트리 3계층 — 스토리. 상태는 문서 frontmatter의 값을 그대로 옮긴다. */
    public record StorySummary(String name, String title, String status) {
    }

    /** 에픽 본문 — 요구사항대로 제목과 목적만 담는다. */
    public record Epic(String name, String title, String purpose) {
    }

    /** 스토리 본문 — 제목·상태·설명·수락 기준·태스크·테스트 6항목. */
    public record Story(
            String name,
            String title,
            String status,
            String updated,
            String description,
            List<AcceptanceCriterion> acceptanceCriteria,
            List<TaskGroup> taskGroups,
            Tests tests
    ) {
    }

    /**
     * 수락 기준 한 줄.
     *
     * @param no   문서에 적힌 AC 번호(예: AC1). 번호는 영구 불변이므로 화면에서 다시 매기지 않고 그대로 쓴다.
     * @param text 번호를 뗀 본문
     * @param done 문서의 체크박스 상태. 열람 전용이라 화면에서 바꾸지 않는다.
     */
    public record AcceptanceCriterion(String no, String text, boolean done) {
    }

    /** 태스크 Role 묶음 — 문서의 <code>### Backend</code> 같은 소제목 단위. */
    public record TaskGroup(String role, List<Task> tasks) {
    }

    /**
     * 태스크 한 줄.
     *
     * @param marker   상태 마커(생성됨/진행중/완료/폐기됨). 마커가 없는 줄이면 null.
     * @param markerAt 마커에 붙은 타임스탬프
     * @param text     마커를 뗀 본문(AC 참조·UI 태그 포함)
     */
    public record Task(String marker, String markerAt, String text) {
    }

    /** 테스트 — 문서의 성공/실패 구분을 합치지 않고 그대로 둔다. */
    public record Tests(List<String> success, List<String> failure) {
    }
}
