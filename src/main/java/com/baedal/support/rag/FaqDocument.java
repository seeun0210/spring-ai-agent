package com.baedal.support.rag;

public record FaqDocument(
        String id,
        String title,
        String category,
        String content
) {
}
