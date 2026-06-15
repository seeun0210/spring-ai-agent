package com.baedal.support.guardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "baedal.guardrail")
public class GuardrailProperties {

    private int maxInputChars = 2000;
    private boolean llmClassifierEnabled = false;
    private List<String> injectionPatterns = new ArrayList<>(List.of(
            "(?i)(system\\s*prompt|시스템\\s*프롬프트|프롬프트\\s*(출력|보여))",
            "(?i)(ignore\\s+(all\\s+)?(previous|above|prior)\\s+instructions|이전\\s*지시\\s*무시)",
            "(?i)(jailbreak|DAN\\s*mode|developer\\s*mode|개발자\\s*모드)",
            "(?i)(now\\s+you\\s+are|너는\\s*이제|역할을\\s*(바꿔|변경))",
            "(?i)(your\\s+rules|너의\\s*규칙|reveal\\s+your\\s+instructions|instructions\\s+reveal)"
    ));
    private List<String> additionalInjectionPatterns = new ArrayList<>();
    private List<String> leakMarkers = new ArrayList<>(List.of(
            "[역할]", "[규칙]", "[금지]", "[Tool 사용 규칙]", "[정책 인용 규칙]",
            "[안전 규칙]", "[응답 포맷]", "[대화 맥락 사용 규칙]"
    ));
    private List<String> additionalLeakMarkers = new ArrayList<>();

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public boolean isLlmClassifierEnabled() {
        return llmClassifierEnabled;
    }

    public void setLlmClassifierEnabled(boolean llmClassifierEnabled) {
        this.llmClassifierEnabled = llmClassifierEnabled;
    }

    public List<String> getInjectionPatterns() {
        return injectionPatterns;
    }

    public void setInjectionPatterns(List<String> injectionPatterns) {
        this.injectionPatterns = injectionPatterns == null ? List.of() : List.copyOf(injectionPatterns);
    }

    public List<String> getAdditionalInjectionPatterns() {
        return additionalInjectionPatterns;
    }

    public void setAdditionalInjectionPatterns(List<String> additionalInjectionPatterns) {
        this.additionalInjectionPatterns = additionalInjectionPatterns == null
                ? List.of()
                : List.copyOf(additionalInjectionPatterns);
    }

    public List<String> getLeakMarkers() {
        return leakMarkers;
    }

    public void setLeakMarkers(List<String> leakMarkers) {
        this.leakMarkers = leakMarkers == null ? List.of() : List.copyOf(leakMarkers);
    }

    public List<String> getAdditionalLeakMarkers() {
        return additionalLeakMarkers;
    }

    public void setAdditionalLeakMarkers(List<String> additionalLeakMarkers) {
        this.additionalLeakMarkers = additionalLeakMarkers == null
                ? List.of()
                : List.copyOf(additionalLeakMarkers);
    }

    public List<String> allInjectionPatterns() {
        List<String> all = new ArrayList<>(injectionPatterns);
        all.addAll(additionalInjectionPatterns);
        return List.copyOf(all);
    }

    public List<String> allLeakMarkers() {
        List<String> all = new ArrayList<>(leakMarkers);
        all.addAll(additionalLeakMarkers);
        return List.copyOf(all);
    }
}
