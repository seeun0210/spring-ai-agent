package com.baedal.support.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentReaderExperimentTest {

    @Test
    void pagePdfDocumentReaderExtractsPolicyTextFromPdf() throws Exception {
        byte[] pdf = pdf("""
                Refund policy
                Refund requests must be submitted within 24 hours after delivery.
                Missing item, wrong delivery, quality issue are eligible reasons.
                """);

        PagePdfDocumentReader reader = new PagePdfDocumentReader(new NamedByteArrayResource("refund-policy.pdf", pdf));

        List<Document> documents = reader.get();

        assertThat(documents)
                .extracting(Document::getText)
                .map(PdfDocumentReaderExperimentTest::normalizeWhitespace)
                .anySatisfy(text -> assertThat(text)
                        .contains("Refund policy")
                        .contains("24 hours after delivery")
                        .contains("quality issue"));
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                for (String line : text.lines().toList()) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
