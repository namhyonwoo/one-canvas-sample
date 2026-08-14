package com.styleseller.onetool.auth;

/** 로그인이 필요한 요청에 세션이 없을 때 던진다. {@link AuthExceptionAdvice} 가 401로 바꾼다. */
public class NotLoggedInException extends RuntimeException {

    public NotLoggedInException(String message) {
        super(message);
    }
}
