package com.styleseller.onetool.member;

/**
 * 한 모드에서 마지막으로 본 문서.
 *
 * @param mode        'canvas' 또는 'issue'
 * @param targetPath  캔버스는 "폴더/파일.html", 이슈는 애자일 이슈 루트 기준 상대 경로
 * @param targetLabel 화면에 보여줄 이름(저장 시점 기준)
 * @param updatedAt   DB(+09:00) 기준 "YYYY-MM-DD HH:MM:SS". 화면이 상대 시각으로 바꿔 보여준다.
 */
public record MemberLastView(String mode, String targetPath, String targetLabel, String updatedAt) {}
