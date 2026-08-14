package com.styleseller.onetool.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 인증 실패를 모든 API에서 같은 모양으로 내려준다.
 *
 * <p>프론트가 401을 보고 로그인 화면으로 보내므로 상태코드와 message 형식이 컨트롤러마다 달라지면 안 된다.
 */
@RestControllerAdvice
public class AuthExceptionAdvice {

    @ExceptionHandler(NotLoggedInException.class)
    public ResponseEntity<Map<String, Object>> handleNotLoggedIn(NotLoggedInException e) {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", e.getMessage()));
    }
}
