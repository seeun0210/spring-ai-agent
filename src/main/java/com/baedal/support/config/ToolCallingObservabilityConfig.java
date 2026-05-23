package com.baedal.support.config;

import com.baedal.support.advisor.ObservedToolCallingManager;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallingObservabilityConfig {

    @Bean
    public ToolCallingManager toolCallingManager(
            ToolCallbackResolver toolCallbackResolver,
            ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ToolCallingObservationConvention> observationConvention
    ) {
        DefaultToolCallingManager delegate = ToolCallingManager.builder()
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolCallbackResolver(toolCallbackResolver)
                .toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
                .build();

        observationConvention.ifAvailable(delegate::setObservationConvention);

        return new ObservedToolCallingManager(delegate);
    }
}
