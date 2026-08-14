-- MySQL 8 방언. spring.sql.init.mode=always 로 매 부팅마다 실행되므로
-- 모든 문장이 멱등이어야 한다. MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않으므로
-- 인덱스를 CREATE TABLE 안에 KEY로 선언해 테이블의 IF NOT EXISTS에 함께 묶는다.
--
-- 인덱스나 UNIQUE가 걸리는 컬럼은 길이를 지정해야 해서 TEXT 대신 VARCHAR를 쓴다.
--
-- 서버 기본 문자셋이 utf8mb3라 이모지가 저장되지 않으므로 테이블마다 utf8mb4를 명시한다.

CREATE TABLE IF NOT EXISTS member (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    nickname    VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    canvas_path VARCHAR(512) NOT NULL,
    block_name  VARCHAR(255) NOT NULL,
    author_id   VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    resolved    TINYINT(1)   NOT NULL DEFAULT 0,
    -- 답글은 부모 코멘트를 참조한다. NULL이면 최상위 코멘트다. 뎁스는 1단계만 사용한다.
    parent_id   BIGINT       NULL,
    -- 코멘트를 블록에 다시 붙이기 위한 앵커 3종. 캔버스 원본을 수정하지 않기 위해
    -- 도구가 렌더 시점에 계산해 저장하고, 조회 시 id → path → hash 순으로 폴백한다.
    anchor_id   VARCHAR(255) NULL,
    anchor_path VARCHAR(512) NULL,
    anchor_hash VARCHAR(128) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_canvas_path (canvas_path),
    KEY idx_comment_parent_id (parent_id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- TaskRepository가 조회하는 테이블. SQLite 시절 schema.sql에 누락돼 있었다.
CREATE TABLE IF NOT EXISTS task (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 사용자가 마지막으로 머문 위치. 모드 선택 화면에서 "이어서 보기"를 제시하는 데 쓴다.
-- 브라우저가 아니라 사용자에 묶이는 정보라 localStorage가 아니라 여기에 둔다.
--
-- member에 FK를 걸지 않는다. comment.author_id도 이메일을 FK 없이 저장하고 있고,
-- FK가 있으면 회원 정리(삭제)가 이 테이블에 막힌다.
CREATE TABLE IF NOT EXISTS member_last_view (
    member_email VARCHAR(255) NOT NULL,
    -- 'canvas' | 'issue'. 모드마다 한 행이라 두 카드 모두에 마지막 위치를 채울 수 있고,
    -- updated_at이 가장 최근인 행이 "최근 머문 모드"가 된다.
    mode         VARCHAR(20)  NOT NULL,
    target_path  VARCHAR(512) NOT NULL,
    -- 화면에 보여줄 이름. 이슈 제목은 마크다운을 파싱해야 나오므로, 카드 두 장 그리자고
    -- 파일을 다시 읽지 않도록 저장 시점의 라벨을 함께 남긴다.
    target_label VARCHAR(255) NOT NULL,
    -- 밀리초까지 둔다. 초 단위면 두 모드를 같은 초에 기록했을 때 '최근 머문 모드' 정렬이
    -- 동률이 되어 배지가 엉뚱한 카드에 붙는다.
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (member_email, mode)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
