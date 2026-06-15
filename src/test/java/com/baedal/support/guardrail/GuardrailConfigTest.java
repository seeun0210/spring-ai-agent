package com.baedal.support.guardrail;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardrailConfigTest {

    @Test
    void inputGuardrailAdvisorIsRegisteredAsSingletonBean() {
        AnnotationConfigApplicationContext context = context();

        InputGuardrailAdvisor first = context.getBean(InputGuardrailAdvisor.class);
        InputGuardrailAdvisor second = context.getBean(InputGuardrailAdvisor.class);

        assertThat(first).isSameAs(second);
        context.close();
    }

    @Test
    void outputGuardrailComponentsAreRegisteredAsSingletonBeans() {
        AnnotationConfigApplicationContext context = context();

        SensitiveDataMasker firstMasker = context.getBean(SensitiveDataMasker.class);
        SensitiveDataMasker secondMasker = context.getBean(SensitiveDataMasker.class);
        OutputGuardrailAdvisor firstAdvisor = context.getBean(OutputGuardrailAdvisor.class);
        OutputGuardrailAdvisor secondAdvisor = context.getBean(OutputGuardrailAdvisor.class);

        assertThat(firstMasker).isSameAs(secondMasker);
        assertThat(firstAdvisor).isSameAs(secondAdvisor);
        context.close();
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient classifierChatClient = mock(ChatClient.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(classifierChatClient);
        context.registerBean(ChatClient.Builder.class, () -> builder);
        context.register(GuardrailConfig.class);
        context.refresh();
        return context;
    }
}
