package com.styleseller.onetool.canvas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CanvasScannerService {

    private final Path rootPath;

    public CanvasScannerService(@Value("${one-canvas.root-path:../one-canvas}") String rootPathStr) {
        Path path = Paths.get(rootPathStr).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            // fallback search if running from root directory
            path = Paths.get("./one-canvas").toAbsolutePath().normalize();
        }
        this.rootPath = path;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public List<CanvasFolderDto> scanFolders() {
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(rootPath)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("sample"))
                    .filter(dir -> !dir.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(dir -> {
                        String folderName = dir.getFileName().toString();
                        List<CanvasFileDto> canvasFiles = scanCanvasFiles(dir);
                        return new CanvasFolderDto(folderName, canvasFiles);
                    })
                    .filter(dto -> !dto.canvases().isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("캔버스 폴더 스캔 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private List<CanvasFileDto> scanCanvasFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("canvas-") && name.endsWith(".html");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::describeCanvas)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * <title>과 확정 meta는 모두 문서 앞부분(head)에만 있으므로 파일 전체를 읽지 않고 앞머리만 훑는다.
     *
     * <p>8KB로도 규칙상 위치(<title> 바로 다음)는 충분히 들어오지만, 그 뒤가 수백 KB짜리
     * &lt;style&gt; 블록이라 meta가 조금만 밀려도 <b>오류 없이 조용히 미확정</b>이 된다. 조용한
     * 오답보다는 조금 더 읽는 편이 낫다.
     */
    private static final int HEAD_SCAN_LIMIT = 32768;

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** "통합캔버스 — 심사 상태 확인" 처럼 모든 캔버스에 반복되는 접두어는 목록에서 잡음일 뿐이라 걷어낸다. */
    private static final Pattern TITLE_PREFIX_PATTERN =
            Pattern.compile("^통합캔버스\\s*[—–-]\\s*");

    private static final Pattern META_PATTERN =
            Pattern.compile("<meta\\s+([^>]*?)/?>", Pattern.CASE_INSENSITIVE);

    /** name/content 속성 순서를 강제하지 않으려고 속성을 따로 훑는다. */
    private static final Pattern ATTR_PATTERN =
            Pattern.compile("([a-zA-Z-]+)\\s*=\\s*\"([^\"]*)\"");

    private CanvasFileDto describeCanvas(Path file) {
        String name = file.getFileName().toString();
        String head = readHead(file);
        if (head == null) {
            return new CanvasFileDto(name, null, false, null, null, null);
        }

        Map<String, String> meta = extractMeta(head);
        boolean confirmed = "confirmed".equalsIgnoreCase(trimToNull(meta.get("canvas-status")));
        return new CanvasFileDto(
                name,
                extractTitle(head),
                confirmed,
                // 확정이 아니면 확정 부속 값은 의미가 없다 — 미확정 캔버스에 남은 값을 화면에 흘리지 않는다.
                confirmed ? isoOrNull(meta.get("canvas-confirmed-at")) : null,
                confirmed ? trimToNull(meta.get("canvas-confirmed-issue")) : null,
                // 수정일은 확정과 무관한 값이라 미확정 캔버스도 그대로 내려보낸다
                // (one-canvas-rule.md 「수정하면 수정일을 남긴다」).
                isoOrNull(meta.get("canvas-updated-at"))
        );
    }

    private String readHead(Path file) {
        char[] buffer = new char[HEAD_SCAN_LIMIT];
        int filled = 0;
        try (Reader reader = Files.newBufferedReader(file)) {
            // read()는 요청한 만큼 채워준다는 보장이 없어, 앞머리를 다 읽을 때까지 돌린다.
            while (filled < buffer.length) {
                int read = reader.read(buffer, filled, buffer.length - filled);
                if (read < 0) {
                    break;
                }
                filled += read;
            }
        } catch (IOException e) {
            return null;
        }
        return filled <= 0 ? null : new String(buffer, 0, filled);
    }

    private Map<String, String> extractMeta(String head) {
        Map<String, String> found = new HashMap<>();
        Matcher tags = META_PATTERN.matcher(head);
        while (tags.find()) {
            String metaName = null;
            String content = null;
            Matcher attrs = ATTR_PATTERN.matcher(tags.group(1));
            while (attrs.find()) {
                if (attrs.group(1).equalsIgnoreCase("name")) {
                    metaName = attrs.group(2).trim().toLowerCase();
                } else if (attrs.group(1).equalsIgnoreCase("content")) {
                    content = attrs.group(2);
                }
            }
            if (metaName != null && metaName.startsWith("canvas-") && content != null) {
                found.put(metaName, content);
            }
        }
        return found;
    }

    /**
     * 시각은 ISO 8601 + 오프셋으로 적기로 되어 있다(one-canvas-rule.md 「확정 상태 기록」).
     * 형식이 깨진 값은 예외를 던지지 않고 없는 값으로 떨어뜨린다 — 캔버스 한 줄 오타로
     * 목록 전체가 죽는 편이 훨씬 나쁘다.
     */
    private String isoOrNull(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            OffsetDateTime.parse(value);
            return value;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    String extractTitle(String head) {
        Matcher matcher = TITLE_PATTERN.matcher(head);
        if (!matcher.find()) {
            return null;
        }

        String title = unescapeHtml(matcher.group(1)).replaceAll("\\s+", " ").trim();
        title = TITLE_PREFIX_PATTERN.matcher(title).replaceFirst("").trim();
        return title.isEmpty() ? null : title;
    }

    private String unescapeHtml(String raw) {
        return raw.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    public String readCanvasContent(String relativePath) {
        Path canvasFile = rootPath.resolve(relativePath).normalize();
        if (!canvasFile.startsWith(rootPath) || !Files.exists(canvasFile)) {
            throw new IllegalArgumentException("캔버스 파일을 찾을 수 없습니다: " + relativePath);
        }
        try {
            return Files.readString(canvasFile);
        } catch (IOException e) {
            throw new RuntimeException("캔버스 파일 읽기 실패: " + e.getMessage(), e);
        }
    }
}
