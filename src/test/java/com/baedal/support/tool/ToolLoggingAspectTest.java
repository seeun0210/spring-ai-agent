package com.baedal.support.tool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baedal.support.order.CancelOrderOutcome;
import com.baedal.support.order.CancelOrderResult;
import com.baedal.support.order.OrderStatus;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolLoggingAspectTest {

    private final ToolLoggingAspect aspect = new ToolLoggingAspect();

    @Test
    void masksSensitiveArgumentAndResultValues() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(ToolLoggingAspect.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            ProceedingJoinPoint joinPoint = joinPoint(
                    "취소 사유: 서울시 강남구 테헤란로 1로 10, 010-1234-5678"
            );

            aspect.logToolCall(joinPoint);

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);

            assertThat(logs).contains("[ADDRESS]");
            assertThat(logs).contains("[PHONE]");
            assertThat(logs).doesNotContain("서울시 강남구 테헤란로");
            assertThat(logs).doesNotContain("010-1234-5678");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    private ProceedingJoinPoint joinPoint(String reason) throws Throwable {
        Method method = DummyOrderTool.class.getMethod("cancelOrder", String.class, String.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"2024-1239", reason});
        when(joinPoint.proceed()).thenReturn(new CancelOrderResult(
                "2024-1239",
                "cancel-1",
                CancelOrderOutcome.CANCELED,
                OrderStatus.CANCELED,
                "주문이 취소되었습니다.",
                reason,
                OffsetDateTime.now()
        ));
        return joinPoint;
    }

    private static class DummyOrderTool {
        @Tool(description = "주문 취소")
        public CancelOrderResult cancelOrder(String orderId, String reason) {
            return null;
        }
    }
}
