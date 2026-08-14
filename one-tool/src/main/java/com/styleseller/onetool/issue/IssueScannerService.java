package com.styleseller.onetool.issue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 레포의 애자일 이슈 디렉터리를 훑어 이슈 보기에 넘긴다.
 *
 * <p>계층은 <code>&lt;도메인&gt;/epic-*∕story-*</code> 이며 디렉터리 계층이 그대로 트리 계층이다
 * (agile-issue/agile-issue-rule.md 「디렉토리 구조와 작성 원칙」). 이슈를 DB로 옮겨 담지 않고 매번
 * 파일에서 읽는다 — 캔버스와 이슈가 항상 최신이어야 한다는 것이 이슈 보기의 전제다.
 *
 * <p>경로 처리는 {@code CanvasScannerService} 와 같은 방식이다(루트 밖 접근 차단).
 */
@Service
public class IssueScannerService {

    /** 규칙 예시 전용 도메인. 실무 이슈와 섞이면 목록이 오염되므로 트리에서 제외한다. */
    private static final String SAMPLE_DIR = "sample";

    private static final String INDEX_FILE = "index.md";
    private static final String EPIC_PREFIX = "epic-";
    private static final String STORY_PREFIX = "story-";

    private final Path rootPath;

    public IssueScannerService(@Value("${agile-issue.root-path:../agile-issue}") String rootPathStr) {
        Path path = Paths.get(rootPathStr).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            // 프로젝트 루트에서 실행하는 경우의 폴백
            path = Paths.get("./agile-issue").toAbsolutePath().normalize();
        }
        this.rootPath = path;
    }

    public Path getRootPath() {
        return rootPath;
    }

    /** 도메인 → 에픽 → 스토리 3계층을 한 번에 내려보낸다. 트리는 진입 즉시 필요하므로 나눠 부르지 않는다. */
    public List<IssueDtos.Domain> scanTree() {
        if (!Files.isDirectory(rootPath)) {
            return Collections.emptyList();
        }
        return childDirs(rootPath).stream()
                .filter(dir -> !dir.getFileName().toString().equals(SAMPLE_DIR))
                // 에픽이 없는 디렉터리는 도메인이 아니다(예: api-doc — API 스펙 보관용).
                .filter(dir -> !epicDirs(dir).isEmpty())
                .map(dir -> new IssueDtos.Domain(
                        dir.getFileName().toString(),
                        titleOf(dir),
                        epicSummaries(dir)))
                .toList();
    }

    public IssueDtos.Epic readEpic(String relativePath) {
        Path dir = resolveInsideRoot(relativePath);
        return IssueMarkdownParser.parseEpic(dir.getFileName().toString(), readIndex(dir));
    }

    public IssueDtos.Story readStory(String relativePath) {
        Path dir = resolveInsideRoot(relativePath);
        return IssueMarkdownParser.parseStory(dir.getFileName().toString(), readIndex(dir));
    }

    // ===== 트리 =====

    private List<IssueDtos.EpicSummary> epicSummaries(Path domainDir) {
        return epicDirs(domainDir).stream()
                .map(epicDir -> new IssueDtos.EpicSummary(
                        epicDir.getFileName().toString(),
                        titleOf(epicDir),
                        storySummaries(epicDir)))
                .toList();
    }

    private List<IssueDtos.StorySummary> storySummaries(Path epicDir) {
        return childDirs(epicDir).stream()
                .filter(dir -> dir.getFileName().toString().startsWith(STORY_PREFIX))
                .map(storyDir -> {
                    String markdown = readIndexOrNull(storyDir);
                    return new IssueDtos.StorySummary(
                            storyDir.getFileName().toString(),
                            markdown == null ? null : IssueMarkdownParser.titleOf(markdown),
                            markdown == null ? null : IssueMarkdownParser.statusOf(markdown));
                })
                .toList();
    }

    private List<Path> epicDirs(Path domainDir) {
        return childDirs(domainDir).stream()
                .filter(dir -> dir.getFileName().toString().startsWith(EPIC_PREFIX))
                .toList();
    }

    /** 이름순 고정 — 목록 순서가 실행마다 달라지면 트리에서 이슈를 눈으로 찾을 수 없다. */
    private List<Path> childDirs(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    // ===== 파일 읽기 =====

    private String titleOf(Path dir) {
        String markdown = readIndexOrNull(dir);
        return markdown == null ? null : IssueMarkdownParser.titleOf(markdown);
    }

    /** 트리를 그리는 도중 index.md 하나가 없다고 트리 전체가 실패하지는 않게 한다. */
    private String readIndexOrNull(Path dir) {
        try {
            return Files.readString(dir.resolve(INDEX_FILE));
        } catch (IOException e) {
            return null;
        }
    }

    private String readIndex(Path dir) {
        Path index = dir.resolve(INDEX_FILE);
        if (!Files.exists(index)) {
            throw new IllegalArgumentException("이슈 문서를 찾을 수 없습니다: " + rootPath.relativize(index));
        }
        try {
            return Files.readString(index);
        } catch (IOException e) {
            throw new RuntimeException("이슈 문서 읽기 실패: " + e.getMessage(), e);
        }
    }

    private Path resolveInsideRoot(String relativePath) {
        Path dir = rootPath.resolve(relativePath).normalize();
        if (!dir.startsWith(rootPath) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("이슈 경로를 찾을 수 없습니다: " + relativePath);
        }
        return dir;
    }
}
