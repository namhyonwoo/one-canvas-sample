package com.styleseller.onetool.issue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 애자일 이슈 <code>index.md</code> 파서.
 *
 * <p>파일을 건드리지 않고 읽기만 한다 — 이슈 문서는 개발자·기획자가 편집하는 원본이고
 * 이슈 보기는 열람 전용이다.
 *
 * <p>파싱 기준은 agile-issue/agile-issue-rule.md 의 템플릿이다.
 * <ul>
 *   <li>frontmatter — <code>status</code>(스토리 필수) · <code>updated</code>(모든 계층 필수)</li>
 *   <li>H1 — <code>&#35; 에픽: name (한글명)</code> 처럼 영문명 뒤 한글명을 괄호로 병기</li>
 *   <li>H2 섹션 — <code>&#35;&#35; 목적</code> / <code>&#35;&#35; User Story</code> /
 *       <code>&#35;&#35; 수락 기준</code> / <code>&#35;&#35; 태스크</code> / <code>&#35;&#35; 테스트</code></li>
 * </ul>
 *
 * <p>문서에 없는 섹션은 지어내지 않고 null·빈 목록으로 둔다. 규칙 문서가 요구하는 섹션이라도
 * 파서가 대신 채우지 않는다 — 비어 있다는 사실이 그대로 보이는 것이 문서를 고칠 신호가 된다.
 */
final class IssueMarkdownParser {

    private IssueMarkdownParser() {
    }

    /** <code>&#35; 에픽: admin-partner-review-detail (총괄 파트너 심사 상세)</code> → 괄호 안 한글명 */
    private static final Pattern H1_PATTERN =
            Pattern.compile("^#\\s+(?:[^:]+:\\s*)?(.*)$");

    private static final Pattern H1_KOREAN_PATTERN =
            Pattern.compile("^(\\S+)\\s*\\((.+)\\)\\s*$");

    /** <code>- [ ] **AC1** Given …</code> / <code>- [x] ~~AC7~~ [폐기됨,…]</code> */
    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile("^[-*]\\s+(?:\\[( |x|X)]\\s+)?(.*)$");

    /** 수락 기준 번호. 폐기 표시(<code>~~AC7~~</code>)도 번호로 인정해 자리를 유지한다. */
    private static final Pattern AC_NO_PATTERN =
            Pattern.compile("^(~~)?(AC\\d+)(~~)?\\s*(.*)$", Pattern.DOTALL);

    /** 태스크 상태 마커 — <code>[완료,2026-08-04 21:08]</code> */
    private static final Pattern TASK_MARKER_PATTERN =
            Pattern.compile("^\\[(생성됨|진행중|완료|폐기됨)\\s*,\\s*([^]]*)]\\s*(.*)$", Pattern.DOTALL);

    static IssueDtos.Epic parseEpic(String dirName, String markdown) {
        List<String> lines = lines(markdown);
        return new IssueDtos.Epic(
                dirName,
                title(lines),
                paragraph(section(lines, "목적"))
        );
    }

    static IssueDtos.Story parseStory(String dirName, String markdown) {
        List<String> lines = lines(markdown);
        Map<String, String> frontmatter = frontmatter(lines);

        return new IssueDtos.Story(
                dirName,
                title(lines),
                frontmatter.get("status"),
                frontmatter.get("updated"),
                paragraph(section(lines, "User Story")),
                acceptanceCriteria(section(lines, "수락 기준")),
                taskGroups(section(lines, "태스크")),
                tests(section(lines, "테스트"))
        );
    }

    /** 트리에 쓸 최소 정보(제목·상태)만 뽑는다. 본문 파싱보다 훨씬 자주 호출되는 경로다. */
    static String titleOf(String markdown) {
        return title(lines(markdown));
    }

    static String statusOf(String markdown) {
        return frontmatter(lines(markdown)).get("status");
    }

    // ===== 공통 =====

    private static List<String> lines(String markdown) {
        if (markdown == null) {
            return List.of();
        }
        return List.of(markdown.split("\r?\n", -1));
    }

    /**
     * frontmatter는 파일 첫 줄 <code>---</code> 부터 다음 <code>---</code> 까지다.
     * 첫 줄이 <code>---</code>가 아니면 frontmatter가 없는 문서로 본다.
     */
    private static Map<String, String> frontmatter(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            return result;
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.equals("---")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                result.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return result;
    }

    /** H1의 <code>영문명 (한글명)</code> 중 한글명을 쓴다. 괄호가 없으면 H1 본문 전체를 쓴다. */
    private static String title(List<String> lines) {
        for (String raw : lines) {
            Matcher h1 = H1_PATTERN.matcher(raw.trim());
            if (raw.startsWith("# ") && h1.matches()) {
                String body = stripInline(h1.group(1)).trim();
                Matcher korean = H1_KOREAN_PATTERN.matcher(body);
                return korean.matches() ? korean.group(2).trim() : (body.isEmpty() ? null : body);
            }
        }
        return null;
    }

    /**
     * <code>## &lt;heading&gt;</code> 부터 다음 <code>##</code> 직전까지를 잘라낸다.
     *
     * <p>제목은 접두 일치로 찾는다 — 규칙 템플릿이 <code>## 수락 기준 (Acceptance Criteria)</code> 처럼
     * 괄호 병기를 허용하기 때문이다.
     */
    private static List<String> section(List<String> lines, String heading) {
        List<String> body = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (inside) {
                    break;
                }
                inside = stripInline(line.substring(3)).trim().startsWith(heading);
                continue;
            }
            if (inside) {
                body.add(line);
            }
        }
        return body;
    }

    /** 인용(<code>&gt;</code>)은 규칙 안내문이라 화면에 옮기지 않는다. */
    private static boolean isNote(String line) {
        return line.trim().startsWith(">");
    }

    private static String paragraph(List<String> body) {
        List<String> kept = new ArrayList<>();
        for (String line : body) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isNote(trimmed) || trimmed.startsWith("### ")) {
                continue;
            }
            kept.add(stripInline(trimmed));
        }
        return kept.isEmpty() ? null : String.join("\n", kept);
    }

    /** 마크다운 강조·코드 표기는 표시용 텍스트에서 걷어낸다(내용은 그대로 남는다). */
    private static String stripInline(String raw) {
        return raw.replace("**", "").replace("`", "");
    }

    // ===== 수락 기준 =====

    private static List<IssueDtos.AcceptanceCriterion> acceptanceCriteria(List<String> body) {
        List<IssueDtos.AcceptanceCriterion> result = new ArrayList<>();
        for (String line : body) {
            String trimmed = line.trim();
            if (isNote(trimmed)) {
                continue;
            }
            Matcher item = LIST_ITEM_PATTERN.matcher(trimmed);
            if (!item.matches()) {
                continue;
            }
            boolean done = item.group(1) != null && !item.group(1).isBlank();
            String text = stripInline(item.group(2)).trim();

            Matcher no = AC_NO_PATTERN.matcher(text);
            if (no.matches()) {
                // 폐기된 AC도 번호 자리를 그대로 유지한다(번호는 재사용되지 않는다).
                String number = no.group(1) != null ? "~~" + no.group(2) + "~~" : no.group(2);
                result.add(new IssueDtos.AcceptanceCriterion(number, no.group(4).trim(), done));
            } else {
                result.add(new IssueDtos.AcceptanceCriterion(null, text, done));
            }
        }
        return result;
    }

    // ===== 태스크 =====

    private static List<IssueDtos.TaskGroup> taskGroups(List<String> body) {
        List<IssueDtos.TaskGroup> groups = new ArrayList<>();
        String role = null;
        List<IssueDtos.Task> tasks = new ArrayList<>();

        for (String line : body) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) {
                if (role != null && !tasks.isEmpty()) {
                    groups.add(new IssueDtos.TaskGroup(role, tasks));
                }
                role = stripInline(trimmed.substring(4)).trim();
                tasks = new ArrayList<>();
                continue;
            }
            if (isNote(trimmed)) {
                continue;
            }
            Matcher item = LIST_ITEM_PATTERN.matcher(trimmed);
            if (!item.matches()) {
                continue;
            }
            tasks.add(task(stripInline(item.group(2)).trim()));
        }
        if (role != null && !tasks.isEmpty()) {
            groups.add(new IssueDtos.TaskGroup(role, tasks));
        }
        // Role 소제목 없이 태스크만 있는 문서도 내용을 잃지 않게 담아 둔다.
        if (groups.isEmpty() && !tasks.isEmpty()) {
            groups.add(new IssueDtos.TaskGroup(null, tasks));
        }
        return groups;
    }

    private static IssueDtos.Task task(String text) {
        Matcher marker = TASK_MARKER_PATTERN.matcher(text);
        if (marker.matches()) {
            return new IssueDtos.Task(marker.group(1), marker.group(2).trim(), marker.group(3).trim());
        }
        return new IssueDtos.Task(null, null, text);
    }

    // ===== 테스트 =====

    private static IssueDtos.Tests tests(List<String> body) {
        List<String> success = new ArrayList<>();
        List<String> failure = new ArrayList<>();
        List<String> current = null;

        for (String line : body) {
            String trimmed = line.trim();
            if (trimmed.startsWith("### ")) {
                String heading = stripInline(trimmed.substring(4)).trim();
                current = heading.startsWith("성공") ? success : heading.startsWith("실패") ? failure : null;
                continue;
            }
            if (current == null || isNote(trimmed)) {
                continue;
            }
            Matcher item = LIST_ITEM_PATTERN.matcher(trimmed);
            if (item.matches()) {
                current.add(stripInline(item.group(2)).trim());
            }
        }
        return new IssueDtos.Tests(success, failure);
    }
}
