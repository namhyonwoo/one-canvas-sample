package com.styleseller.onetool.canvas;

/**
 * 사이드바 목록 한 줄에 해당하는 캔버스 파일.
 *
 * <p>확정 3종은 캔버스 html {@code <head>}의 meta에서 읽는다. 확정이란 "이 캔버스에서 애자일
 * 이슈를 최초로 생성했다"는 뜻이며(one-canvas-rule.md 「확정 상태 기록」), 기록은 이슈를 만드는
 * 쪽이 남긴다 — one-tool은 읽기만 한다.
 *
 * @param name           캔버스 파일명 (예: canvas-partner-review-status.html)
 * @param title          캔버스 HTML의 &lt;title&gt;에서 뽑아낸 한글 타이틀. 없으면 null.
 * @param confirmed      확정 여부. {@code canvas-status=confirmed} meta가 없으면 false.
 * @param confirmedAt    최초 이슈 생성 시각(ISO 8601). 미확정이면 null.
 * @param confirmedIssue 그때 만든 에픽 경로(저장소 루트 기준). 미확정이면 null.
 * @param updatedAt      캔버스를 마지막으로 고친 시각. 확정과 무관하게 모든 캔버스가 갖는 값이며,
 *                       확정 시각보다 뒤이면 "확정 후 수정됨"으로 읽힌다. 값이 없거나 형식이 깨지면 null.
 */
public record CanvasFileDto(
        String name,
        String title,
        boolean confirmed,
        String confirmedAt,
        String confirmedIssue,
        String updatedAt
) {}
