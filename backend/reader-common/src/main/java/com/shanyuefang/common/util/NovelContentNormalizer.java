package com.shanyuefang.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared deterministic cleanup used by source fetching and RAG indexing. */
public final class NovelContentNormalizer {
    public static final String VERSION = "novel-normalizer-v1";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");
    private static final Pattern HTML = Pattern.compile("<[^>]{1,300}>");
    private static final Pattern AD = Pattern.compile("(?im)^(?:\\x{672c}\\x{7ae0}\\x{5b8c}|\\x{5168}\\x{6587}\\x{5b8c}|\\x{6700}\\x{65b0}\\x{7f51}\\x{5740}|\\x{8bf7}\\x{6536}\\x{85cf}|\\x{624b}\\x{673a}\\x{7528}\\x{6237}\\x{8bf7}\\x{6d4f}\\x{89c8}|www\\.|https?://).*$\\R?");
    private static final Pattern MOJIBAKE = Pattern.compile("(?:\\x{fffd}+|\\x{9523}\\x{65a4}\\x{62f7}|\\x{9523}|\\x{00c3}|\\x{00c2}|\\x{95bf}\\x{71b8}\\x{67bb}\\x{93b7})");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{5,}");
    private static final Pattern REPEATED_TOKEN = Pattern.compile("^(.{2,4})\\1{1,}$");
    private static final String CORRUPTION_MARKERS = "\u9523\u65a4\u62f7\u95bf\u71b8\u67bb\u93b7\u9513\u6587\u67b6";

    private NovelContentNormalizer() { }

    public static Result analyze(String raw) {
        String input = raw == null ? "" : raw;
        String normalized = Normalizer.normalize(input.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFKC);
        normalized = HTML.matcher(normalized).replaceAll(" ");
        normalized = CONTROL.matcher(normalized).replaceAll("");
        normalized = AD.matcher(normalized).replaceAll("");
        normalized = removeCorruptLines(normalized);
        normalized = normalizeWhitespace(normalized).trim();
        return new Result(sha256(input), sha256(normalized), simHash(normalized), quality(normalized), normalized);
    }

    private static String removeCorruptLines(String value) {
        StringBuilder kept = new StringBuilder(value.length());
        for (String line : value.split("\\n", -1)) {
            String candidate = line.trim();
            if (!candidate.isEmpty() && (isMojibake(candidate) || isRepeatedCorruption(candidate))) continue;
            kept.append(line).append('\n');
        }
        return kept.toString();
    }

    private static boolean isMojibake(String value) {
        if (MOJIBAKE.matcher(value).find()) return true;
        if (value.contains("閿熸枻鎷") || value.contains("敓鏂ゆ嫹")) return true;
        int markers = 0;
        for (int i = 0; i < value.length(); i++) if (CORRUPTION_MARKERS.indexOf(value.charAt(i)) >= 0) markers++;
        return markers >= 3 && markers * 2 >= value.length();
    }

    private static boolean isRepeatedCorruption(String value) {
        if (value.length() > 120) return false;
        int distinct = (int) value.chars().distinct().count();
        return distinct <= 4 && (value.indexOf('\u62f7') >= 0 || value.indexOf('\u65a4') >= 0 || REPEATED_TOKEN.matcher(value).matches());
    }

    private static String normalizeWhitespace(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '\t') { pendingSpace = true; continue; }
            if (pendingSpace && out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append(' ');
            pendingSpace = false;
            out.append(c);
        }
        return out.toString().replaceAll("[ ]{2,}", " ");
    }

    private static double quality(String value) {
        if (value.isBlank()) return 0D;
        int useful = 0, han = 0, bad = 0, punctuation = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) continue;
            useful++;
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) han++;
            if (c == '\uFFFD' || c == '\u9523' || c == '\u62F7') bad++;
            if ("，。！？；：、,.!?;:".indexOf(c) >= 0) punctuation++;
        }
        double textRatio = useful == 0 ? 0 : (double) han / useful;
        double badRatio = useful == 0 ? 1 : (double) bad / useful;
        double repeatPenalty = REPEATED.matcher(value).find() ? 0.18 : 0;
        double mojibakePenalty = MOJIBAKE.matcher(value).find() ? 0.25 : 0;
        return Math.max(0, Math.min(1, 0.72 * textRatio + 0.18 * Math.min(1, punctuation / 80D) + 0.10 - badRatio - repeatPenalty - mojibakePenalty));
    }

    private static long simHash(String value) {
        if (value.isBlank()) return 0L;
        int[] bits = new int[64];
        String compact = value.replaceAll("\\s+", "");
        for (int i = 0; i + 3 <= compact.length(); i++) {
            long hash = fnv1a(compact.substring(i, i + 3).toLowerCase(Locale.ROOT));
            for (int bit = 0; bit < 64; bit++) bits[bit] += ((hash >>> bit) & 1L) == 1L ? 1 : -1;
        }
        long result = 0;
        for (int bit = 0; bit < 64; bit++) if (bits[bit] >= 0) result |= 1L << bit;
        return result;
    }

    private static long fnv1a(String value) { long h = 0xcbf29ce484222325L; for (byte b : value.getBytes(StandardCharsets.UTF_8)) h = (h ^ (b & 0xff)) * 0x100000001b3L; return h; }
    private static String sha256(String value) {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(64); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString(); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public static double similarity(long left, long right) { return 1D - Long.bitCount(left ^ right) / 64D; }
    public record Result(String rawHash, String normalizedHash, long semanticFingerprint, double qualityScore, String normalizedContent) { }
}
