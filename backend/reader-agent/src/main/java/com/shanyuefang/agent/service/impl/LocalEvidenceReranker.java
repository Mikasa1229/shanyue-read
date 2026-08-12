package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.service.RerankerService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Local, dependency-free precision ranking for bounded chapter evidence.
 * It combines normalized semantic recall, BM25-style lexical relevance,
 * citation quality, and cross-source corroboration.
 */
final class LocalEvidenceReranker {
    private static final Pattern TERM = Pattern.compile("[\\p{IsHan}]{2,6}|[A-Za-z0-9]{2,}");
    private static final double K1 = 1.2D;
    private static final double B = 0.75D;

    private LocalEvidenceReranker() {
    }

    static List<RerankerService.Candidate> rank(String query,
                                                List<RerankerService.Candidate> candidates,
                                                int limit) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<String> terms = terms(query);
        double averageLength = candidates.stream()
                .mapToInt(candidate -> tokens(candidate.content()).size())
                .average().orElse(1D);
        Map<RerankerService.Candidate, Double> scores = new HashMap<>();
        for (RerankerService.Candidate candidate : candidates) {
            double semantic = normalize(candidate.retrievalScore());
            double lexical = bm25(terms, query, candidate.content(), candidates, averageLength);
            double evidence = evidenceQuality(candidate.content());
            double corroboration = corroboration(candidate.sources());
            scores.put(candidate, 0.35D * semantic + 0.45D * lexical
                    + 0.10D * evidence + 0.10D * corroboration);
        }
        int safeLimit = Math.max(1, Math.min(limit, candidates.size()));
        return candidates.stream()
                .sorted(Comparator.comparingDouble((RerankerService.Candidate value) -> scores.getOrDefault(value, 0D))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(RerankerService.Candidate::retrievalScore).reversed())
                        .thenComparing(RerankerService.Candidate::content, Comparator.nullsLast(String::compareTo)))
                .limit(safeLimit)
                .toList();
    }

    private static double bm25(List<String> queryTerms, String query, String content,
                               List<RerankerService.Candidate> candidates, double averageLength) {
        if (queryTerms.isEmpty() || content == null || content.isBlank()) return 0D;
        List<String> document = tokens(content);
        if (document.isEmpty()) return 0D;
        Set<String> documentTerms = new HashSet<>(document);
        Map<String, Integer> frequencies = new HashMap<>();
        document.forEach(term -> frequencies.merge(term, 1, Integer::sum));
        double score = 0D;
        for (String term : queryTerms) {
            int frequency = frequencies.getOrDefault(term, 0);
            if (frequency == 0) continue;
            long documentFrequency = candidates.stream()
                    .filter(candidate -> tokens(candidate.content()).contains(term))
                    .count();
            double idf = Math.log(1D + (candidates.size() - documentFrequency + 0.5D)
                    / (documentFrequency + 0.5D));
            double denominator = frequency + K1 * (1D - B + B * document.size() / Math.max(1D, averageLength));
            score += idf * (frequency * (K1 + 1D) / denominator);
        }
        double coverage = queryTerms.stream().filter(documentTerms::contains).distinct().count()
                / (double) queryTerms.size();
        if (query != null && !query.isBlank()
                && content.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim())) {
            coverage = Math.min(1D, coverage + 0.35D);
        }
        double bm25Score = score / Math.max(1D, queryTerms.size() * 1.8D);
        return Math.min(1D, 0.65D * coverage + 0.35D * bm25Score);
    }

    private static double evidenceQuality(String content) {
        if (content == null || content.isBlank()) return 0D;
        String value = content.trim();
        double citation = value.matches("^\\[Chapter \\d+].*") ? 1D : 0D;
        int bodyLength = value.replaceFirst("^\\[Chapter \\d+]\\s*", "").length();
        double length = bodyLength >= 80 && bodyLength <= 900 ? 1D
                : Math.min(1D, Math.max(0D, bodyLength / 80D));
        return 0.65D * citation + 0.35D * length;
    }

    private static double corroboration(String sources) {
        if (sources == null || sources.isBlank()) return 0D;
        Set<String> unique = new HashSet<>();
        for (String source : sources.split(",")) {
            if (!source.isBlank()) unique.add(source.trim().toUpperCase(Locale.ROOT));
        }
        return Math.min(1D, unique.size() / 3D);
    }

    private static double normalize(double value) {
        if (!Double.isFinite(value)) return 0.5D;
        // A bounded transform prevents one high-recall outlier from drowning out
        // an exact lexical match or independently corroborated evidence.
        double bounded = value / (1D + Math.abs(value));
        return Math.min(1D, Math.max(0D, (bounded + 1D) / 2D));
    }

    private static List<String> terms(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        TERM.matcher(text).results().forEach(match -> expand(match.group(), result));
        return result.stream().distinct().toList();
    }

    private static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        TERM.matcher(text).results().forEach(match -> expand(match.group(), result));
        return result;
    }

    private static void expand(String value, List<String> output) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.chars().allMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)) {
            output.add(normalized);
            return;
        }
        int length = normalized.length();
        for (int n = 2; n <= Math.min(4, length); n++) {
            for (int start = 0; start + n <= length; start++) {
                output.add(normalized.substring(start, start + n));
            }
        }
    }
}
