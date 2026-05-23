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

@Slf4j
@Aspect
@Component
public class ToolLoggingAspect {

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
            joiner.add(parameterNames[i] + "=" + args[i]);
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
                    cancelResult.canceledReason(),
                    cancelResult.canceledAt()
            );
            return;
        }

        if (result == null) {
            log.info("[Tool] {} result(null)", methodName);
        }
    }
}
