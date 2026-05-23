package com.baedal.support.tool;

import com.baedal.support.order.CancelOrderResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
public class ToolLoggingAspect {

    private static final int MAX_LOG_VALUE_LENGTH = 120;
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\b\\d{2,6}-\\d{2,6}-\\d{2,8}\\b");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(서울시|서울|경기도|부산시|대구시|인천시|광주시|대전시|울산시|세종시)[^\\n\",]{0,40}(로|길)\\s*\\d+");

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String methodName = method.getName();

        log.info("[Tool] {}({})", methodName, formatArguments(methodName, joinPoint.getArgs()));

        Object result = joinPoint.proceed();
        logResult(methodName, result);
        return result;
    }

    private String formatArguments(String methodName, Object[] args) {
        String[] parameterNames = parameterNames(methodName, args.length);
        StringJoiner joiner = new StringJoiner(", ");

        for (int i = 0; i < args.length; i++) {
            joiner.add(parameterNames[i] + "=" + safeValue(args[i]));
        }

        return joiner.toString();
    }

    private String[] parameterNames(String methodName, int parameterCount) {
        if (parameterCount == 1 && ("getOrderDetail".equals(methodName) || "getDeliveryStatus".equals(methodName))) {
            return new String[]{"orderId"};
        }

        if (parameterCount == 2 && "cancelOrder".equals(methodName)) {
            return new String[]{"orderId", "reason"};
        }

        String[] names = new String[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }

    private void logResult(String methodName, Object result) {
        if (result instanceof CancelOrderResult cancelResult) {
            log.info(
                    "[Tool] {} result(orderId={}, cancelId={}, outcome={}, status={}, canceledReason={}, canceledAt={})",
                    methodName,
                    cancelResult.orderId(),
                    cancelResult.cancelId(),
                    cancelResult.outcome(),
                    cancelResult.status(),
                    safeValue(cancelResult.canceledReason()),
                    cancelResult.canceledAt()
            );
            return;
        }

        if (result == null) {
            log.info("[Tool] {} result(null)", methodName);
            return;
        }

        log.info(
                "[Tool] {} result(type={}, id={}, summary={})",
                methodName,
                result.getClass().getSimpleName(),
                extractId(result),
                safeValue(result)
        );
    }

    private String extractId(Object result) {
        try {
            Method orderId = result.getClass().getMethod("orderId");
            Object id = orderId.invoke(result);
            return safeValue(id);
        } catch (ReflectiveOperationException ignored) {
            return "n/a";
        }
    }

    private String safeValue(Object value) {
        if (value == null) {
            return "null";
        }

        String sanitized = String.valueOf(value);
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[PHONE]");
        sanitized = ACCOUNT_PATTERN.matcher(sanitized).replaceAll("[ACCOUNT]");
        sanitized = ADDRESS_PATTERN.matcher(sanitized).replaceAll("[ADDRESS]");
        if (sanitized.length() > MAX_LOG_VALUE_LENGTH) {
            return sanitized.substring(0, MAX_LOG_VALUE_LENGTH) + "...[TRUNCATED]";
        }
        return sanitized;
    }
}
