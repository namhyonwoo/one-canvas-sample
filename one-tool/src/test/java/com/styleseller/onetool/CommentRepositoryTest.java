package com.styleseller.onetool;

import com.styleseller.onetool.comment.Comment;
import com.styleseller.onetool.comment.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ActiveProfiles가 없으면 개발용 DB(./data/one-tool.db)에 테스트 데이터가 쌓인다.
@SpringBootTest
@ActiveProfiles("test")
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Test
    void saveAndFindComments() {
        Comment comment = new Comment("collab-review/canvas-admin-canvas-viewer.html", "에픽 표", "kuyanam@styleseller.co.kr", "테스트 코멘트입니다.");
        Comment saved = commentRepository.save(comment);

        assertThat(saved.getId()).isNotNull();

        List<Comment> list = commentRepository.findByCanvasPath("collab-review/canvas-admin-canvas-viewer.html");
        assertThat(list).isNotEmpty();
        assertThat(list.stream().anyMatch(c -> c.getBody().equals("테스트 코멘트입니다."))).isTrue();
    }

    @Test
    void 최상위_코멘트의_parentId는_null이고_답글은_부모를_가리킨다() {
        Comment parent = commentRepository.save(
                new Comment("collab-review/reply.html", "블록", "kuyanam@styleseller.co.kr", "부모 코멘트"));
        // getLong은 NULL을 0으로 돌려주므로, wasNull 처리가 빠지면 여기서 0이 잡힌다.
        assertThat(parent.getParentId()).isNull();

        Comment reply = commentRepository.save(
                new Comment("collab-review/reply.html", "블록", "kuyanam@styleseller.co.kr", "답글", parent.getId()));
        assertThat(reply.getParentId()).isEqualTo(parent.getId());

        // 테스트 DB는 실행 간 유지되므로 절대 개수가 아니라 방금 저장한 관계만 확인한다.
        List<Comment> list = commentRepository.findByCanvasPath("collab-review/reply.html");
        assertThat(list).anyMatch(c -> c.getId().equals(reply.getId()) && parent.getId().equals(c.getParentId()));
        assertThat(list).anyMatch(c -> c.getId().equals(parent.getId()) && c.getParentId() == null);
    }

    @Test
    void 앵커_3종이_저장되고_그대로_조회된다() {
        Comment comment = new Comment("collab-review/anchor.html", "블록", "kuyanam@styleseller.co.kr", "앵커 있는 코멘트");
        comment.setAnchor("id:screen-detail", "h2#3", "c8c58107");

        Comment saved = commentRepository.save(comment);

        assertThat(saved.getAnchorId()).isEqualTo("id:screen-detail");
        assertThat(saved.getAnchorPath()).isEqualTo("h2#3");
        assertThat(saved.getAnchorHash()).isEqualTo("c8c58107");
    }

    @Test
    void 앵커가_없는_코멘트는_null로_남는다() {
        Comment saved = commentRepository.save(
                new Comment("collab-review/no-anchor.html", "블록", "kuyanam@styleseller.co.kr", "앵커 없는 코멘트"));

        assertThat(saved.getAnchorId()).isNull();
        assertThat(saved.getAnchorPath()).isNull();
        assertThat(saved.getAnchorHash()).isNull();
    }

    @Test
    void resolve는_해결_상태를_토글한다() {
        Comment saved = commentRepository.save(
                new Comment("collab-review/resolve.html", "블록", "kuyanam@styleseller.co.kr", "해결 대상"));
        assertThat(saved.isResolved()).isFalse();

        assertThat(commentRepository.resolve(saved.getId(), true).isResolved()).isTrue();
        assertThat(commentRepository.resolve(saved.getId(), false).isResolved()).isFalse();
    }

    @Test
    void findByIdOrNull은_없는_id에_예외를_던지지_않는다() {
        assertThat(commentRepository.findByIdOrNull(999_999L)).isNull();
    }

    @Test
    void 미해결_집계는_해결된_코멘트와_답글을_제외한다() {
        // 테스트 DB는 실행 간 유지되므로 매 실행마다 새 canvasPath를 써야 개수가 누적되지 않는다.
        String canvasPath = "collab-review/unresolved-" + UUID.randomUUID() + ".html";

        commentRepository.save(new Comment(canvasPath, "블록", "kuyanam@styleseller.co.kr", "미해결 1"));
        Comment second = commentRepository.save(
                new Comment(canvasPath, "블록", "kuyanam@styleseller.co.kr", "미해결 2"));
        Comment resolved = commentRepository.save(
                new Comment(canvasPath, "블록", "kuyanam@styleseller.co.kr", "해결될 것"));
        commentRepository.resolve(resolved.getId(), true);
        // 답글은 부모의 해결 상태를 따르므로 집계에서 빠져야 한다.
        commentRepository.save(
                new Comment(canvasPath, "블록", "kuyanam@styleseller.co.kr", "답글", second.getId()));

        assertThat(commentRepository.countUnresolvedByCanvasPath()).containsEntry(canvasPath, 2);
    }

    @Test
    void 미해결이_없는_캔버스는_집계에_들어가지_않는다() {
        String canvasPath = "collab-review/all-resolved-" + UUID.randomUUID() + ".html";
        Comment only = commentRepository.save(
                new Comment(canvasPath, "블록", "kuyanam@styleseller.co.kr", "해결될 것"));
        commentRepository.resolve(only.getId(), true);

        assertThat(commentRepository.countUnresolvedByCanvasPath()).doesNotContainKey(canvasPath);
    }
}
