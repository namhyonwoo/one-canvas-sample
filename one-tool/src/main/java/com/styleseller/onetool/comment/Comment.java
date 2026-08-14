package com.styleseller.onetool.comment;

public class Comment {
    private Long id;
    private String canvasPath;
    private String blockName;
    private String authorId;
    /**
     * 화면에 보여줄 작성자 이름. author_id(이메일)로 member를 조회해 조회 시점에 채운다.
     * 코멘트에 닉네임을 복사해두면 회원이 닉네임을 바꿨을 때 과거 코멘트만 옛 이름으로 남는다.
     * 탈퇴/미등록 계정이면 null이므로 화면에서는 이메일로 폴백한다.
     */
    private String authorNickname;
    private String body;
    private boolean resolved;
    /** 답글이면 부모 코멘트 id, 최상위 코멘트면 null. */
    private Long parentId;
    /**
     * 코멘트를 블록에 다시 붙이기 위한 앵커 3종. 캔버스 원본에 코멘트용 속성을 심지 않기 위해
     * 도구가 렌더 시점에 계산한다. 조회 시 id → path → hash 순으로 폴백하므로
     * 셋 중 일부만 채워져 있어도 된다(구버전 코멘트는 전부 null).
     */
    private String anchorId;
    private String anchorPath;
    private String anchorHash;
    private String createdAt;

    public Comment() {}

    public Comment(Long id, String canvasPath, String blockName, String authorId, String body, boolean resolved, Long parentId, String createdAt) {
        this.id = id;
        this.canvasPath = canvasPath;
        this.blockName = blockName;
        this.authorId = authorId;
        this.body = body;
        this.resolved = resolved;
        this.parentId = parentId;
        this.createdAt = createdAt;
    }

    public Comment(String canvasPath, String blockName, String authorId, String body) {
        this(canvasPath, blockName, authorId, body, null);
    }

    public Comment(String canvasPath, String blockName, String authorId, String body, Long parentId) {
        this.canvasPath = canvasPath;
        this.blockName = blockName;
        this.authorId = authorId;
        this.body = body;
        this.resolved = false;
        this.parentId = parentId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCanvasPath() { return canvasPath; }
    public void setCanvasPath(String canvasPath) { this.canvasPath = canvasPath; }

    public String getBlockName() { return blockName; }
    public void setBlockName(String blockName) { this.blockName = blockName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String authorNickname) { this.authorNickname = authorNickname; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getAnchorId() { return anchorId; }
    public void setAnchorId(String anchorId) { this.anchorId = anchorId; }

    public String getAnchorPath() { return anchorPath; }
    public void setAnchorPath(String anchorPath) { this.anchorPath = anchorPath; }

    public String getAnchorHash() { return anchorHash; }
    public void setAnchorHash(String anchorHash) { this.anchorHash = anchorHash; }

    public void setAnchor(String anchorId, String anchorPath, String anchorHash) {
        this.anchorId = anchorId;
        this.anchorPath = anchorPath;
        this.anchorHash = anchorHash;
    }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
