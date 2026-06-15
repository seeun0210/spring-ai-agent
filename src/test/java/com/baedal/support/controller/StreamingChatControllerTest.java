package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import com.baedal.support.service.StreamingChatService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamingChatControllerTest {

    @Test
    void streamDelegatesToStreamingChatService() {
        StreamingChatService streamingChatService = mock(StreamingChatService.class);
        StreamingChatController controller = new StreamingChatController(streamingChatService);
        when(streamingChatService.stream("상담원 연결해 주세요"))
                .thenReturn(Flux.just("상담원 연결 안내 1600-0987"));

        List<String> chunks = controller.chatStream(new ChatRequest("상담원 연결해 주세요"))
                .collectList()
                .block();

        assertThat(chunks).containsExactly("상담원 연결 안내 1600-0987");
        verify(streamingChatService).stream("상담원 연결해 주세요");
    }
}
