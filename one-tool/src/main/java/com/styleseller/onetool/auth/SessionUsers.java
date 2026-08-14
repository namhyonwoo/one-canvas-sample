package com.styleseller.onetool.auth;

import org.springframework.web.server.WebSession;

/**
 * 세션에서 로그인 사용자를 꺼내는 공통 지점.
 *
 * <p>로그인 필수 지점이 코멘트 한 곳이던 시절에는 컨트롤러 안에 두었지만, 캔버스·이슈까지 인증이
 * 붙으면서 같은 판단이 네 곳으로 늘었다. 세션 키("USER_EMAIL")를 아는 곳을 하나로 모아 둔다.
 */
public final class SessionUsers {

    /** 로그인 시 {@code MemberApiController} 가 넣는 세션 키. */
    public static final String USER_EMAIL = "USER_EMAIL";

    private SessionUsers() {}

    /**
     * 로그인한 사용자의 이메일. 세션이 비어 있으면 {@link NotLoggedInException} 으로 끊는다.
     * 호출부가 null 검사를 잊어도 익명 요청이 통과하지 않게 하려는 것이다.
     */
    public static String requireLogin(WebSession session) {
        String email = session.getAttribute(USER_EMAIL);
        if (email == null) {
            throw new NotLoggedInException("로그인이 필요합니다.");
        }
        return email;
    }

    /** 로그인 여부만 필요한 곳에서 쓴다. */
    public static boolean isLoggedIn(WebSession session) {
        return session.getAttribute(USER_EMAIL) != null;
    }
}
