package com.shanyuefang.agent.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps every prompt contributor inside one deterministic input budget. */
public final class PromptContextBudget {
    private final int maxTokens;
    private final StringBuilder prompt = new StringBuilder();
    private final Map<String, Integer> acceptedTokens = new LinkedHashMap<>();

    public PromptContextBudget(int maxTokens) {
        this.maxTokens = Math.max(1, maxTokens);
    }

    public boolean add(String section, String value) {
        if (value == null || value.isBlank()) return false;
        int remainingChars = Math.max(0, (maxTokens - totalTokens()) * 4);
        if (remainingChars < 4) return false;
        String bounded = value.length() <= remainingChars ? value : value.substring(0, remainingChars);
        if (prompt.length() > 0) prompt.append("\n\n");
        prompt.append(bounded);
        acceptedTokens.merge(section, estimateTokens(bounded), Integer::sum);
        return bounded.length() == value.length();
    }

    public String text() { return prompt.toString(); }
    public int tokens(String section) { return acceptedTokens.getOrDefault(section, 0); }
    public int totalTokens() { return acceptedTokens.values().stream().mapToInt(Integer::intValue).sum(); }
    public static int estimateTokens(String value) { return value == null || value.isBlank() ? 0 : Math.max(1, (value.length() + 3) / 4); }
}
