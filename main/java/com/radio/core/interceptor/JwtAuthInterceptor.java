package com.radio.core.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.common.ApiResponse;
import com.radio.core.context.UserContext;
import com.radio.core.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器
 */
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            writeUnauthorized(response, "未登录或Token为空");
            return false;
        }

        String token = authorization;
        if (authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Object userIdObj = claims.get("userId");
            Object usernameObj = claims.get("username");

            if (userIdObj == null || usernameObj == null) {
                writeUnauthorized(response, "Token无效");
                return false;
            }

            UserContext.setUserId(Long.parseLong(userIdObj.toString()));
            UserContext.setUsername(usernameObj.toString());
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "登录已过期或Token非法");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> result = ApiResponse.fail(401, msg);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}