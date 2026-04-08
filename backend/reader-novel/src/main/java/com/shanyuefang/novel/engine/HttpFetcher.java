package com.shanyuefang.novel.engine;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量 HTTP 抓取器，基于 Hutool HttpUtil。
 * 支持 GET / POST，忽略 SSL 证书和主机名验证。
 */
@Slf4j
public class HttpFetcher {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 3_000;
    private static final int READ_TIMEOUT    = 6_000;

    private static final SSLContext TRUST_ALL_CTX;

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            TRUST_ALL_CTX = SSLContext.getInstance("TLS");
            TRUST_ALL_CTX.init(null, trustAll, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 抓取 URL，自动处理 legado 格式附加的请求配置 JSON。
     *
     * @param rawUrl      原始 URL（可能带 ,{...} 附加配置）
     * @param headerJson  书源级别 header JSON 字符串
     * @param baseUrl     书源 base URL，用于将相对 URL 转为绝对 URL
     * @param charsetHint 书源指定的编码（如 "GBK"），null 表示自动检测
     */
    @SuppressWarnings("unchecked")
    public static String fetch(String rawUrl, String headerJson, String baseUrl, String charsetHint) throws IOException {
        // 将相对 URL 转为绝对 URL
        String resolvedUrl = resolveUrl(rawUrl, baseUrl);

        String url = resolvedUrl;
        String method = "GET";
        String bodyStr = null;
        Map<String, String> extraHeaders = new LinkedHashMap<>();

        // URL 附加 JSON 中的 charset 优先级最高
        String charset = charsetHint;

        // 解析 URL 末尾附加的 JSON 配置，例如：
        // /search.html,{'method':'POST','body':'s={{key}}','charset':'GBK'}
        int jsonStart = findAppendedJsonStart(resolvedUrl);
        if (jsonStart > 0) {
            url = resolvedUrl.substring(0, jsonStart).trim();
            String jsonPart = resolvedUrl.substring(jsonStart + 1)
                    // legado 里有时用单引号，Jackson 不支持，先替换
                    .replace("'", "\"");
            try {
                Map<String, Object> cfg = OM.readValue(jsonPart, Map.class);
                String m = (String) cfg.get("method");
                if (m != null) method = m.toUpperCase();

                Object b = cfg.get("body");
                if (b instanceof String) bodyStr = (String) b;
                else if (b instanceof Map) bodyStr = OM.writeValueAsString(b);

                Object h = cfg.get("headers");
                if (h instanceof Map) {
                    ((Map<String, Object>) h).forEach((k, v) ->
                            extraHeaders.put(k, String.valueOf(v)));
                }
                // URL 附加 JSON 中的 charset 覆盖书源级别设置
                Object cs = cfg.get("charset");
                if (cs != null) charset = String.valueOf(cs);
            } catch (Exception e) {
                log.debug("书源 URL 附加 JSON 解析失败: {}", jsonPart);
            }
        }

        // 书源级别 header
        if (StringUtils.hasText(headerJson)) {
            try {
                Map<String, Object> hMap = OM.readValue(headerJson, Map.class);
                hMap.forEach((k, v) -> extraHeaders.putIfAbsent(k, String.valueOf(v)));
            } catch (Exception e) {
                log.debug("书源 header JSON 解析失败: {}", headerJson);
            }
        }

        try {
            HttpRequest req;
            if ("POST".equals(method)) {
                req = HttpUtil.createPost(url);
                if (StringUtils.hasText(bodyStr)) {
                    // 判断 body 类型：JSON 还是表单
                    if (bodyStr.trim().startsWith("{")) {
                        req.body(bodyStr, "application/json");
                    } else {
                        req.body(bodyStr, "application/x-www-form-urlencoded");
                    }
                }
            } else {
                req = HttpUtil.createGet(url, true);
            }

            req.setConnectionTimeout(CONNECT_TIMEOUT)
               .setReadTimeout(READ_TIMEOUT)
               .header("User-Agent", extraHeaders.getOrDefault("User-Agent", DEFAULT_UA))
               .header("Accept", "text/html,application/xhtml+xml,application/json,*/*")
               .header("Accept-Language", "zh-CN,zh;q=0.9")
               .setSSLSocketFactory(TRUST_ALL_CTX.getSocketFactory())
               .setHostnameVerifier((host, session) -> true);

            extraHeaders.forEach((k, v) -> {
                if (!"User-Agent".equalsIgnoreCase(k)) req.header(k, v);
            });

            final String finalCharset = charset;
            log.debug("{} 抓取: {} charset={}", method, url, finalCharset != null ? finalCharset : "auto");
            try (HttpResponse resp = req.execute()) {
                if (resp.getStatus() >= 400) {
                    throw new IOException("HTTP " + resp.getStatus() + " 请求失败: " + url);
                }
                // 若指定了编码，使用字节流解码；否则让 Hutool 自动检测
                if (StringUtils.hasText(finalCharset)) {
                    byte[] bytes = resp.bodyBytes();
                    return new String(bytes, java.nio.charset.Charset.forName(finalCharset));
                }
                return resp.body();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("HTTP 请求异常: " + e.getMessage(), e);
        }
    }

    /** 兼容旧调用（不指定编码） */
    public static String fetch(String rawUrl, String headerJson, String baseUrl) throws IOException {
        return fetch(rawUrl, headerJson, baseUrl, null);
    }

    /** 兼容旧调用 */
    public static String fetch(String rawUrl, String headerJson) throws IOException {
        return fetch(rawUrl, headerJson, null, null);
    }

    /** 将相对 URL 转为绝对 URL */
    public static String resolveUrl(String url, String base) {
        if (!StringUtils.hasText(url)) return url;
        // 找到附加 JSON 部分（逗号 + {...}）的位置
        int jsonStart = findAppendedJsonStart(url);
        String urlPart = jsonStart > 0 ? url.substring(0, jsonStart) : url;
        String jsonPart = jsonStart > 0 ? url.substring(jsonStart) : "";

        urlPart = urlPart.trim();
        if (urlPart.startsWith("http://") || urlPart.startsWith("https://")) {
            return url; // 已是绝对 URL
        }
        if (!StringUtils.hasText(base)) return url;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String resolved = urlPart.startsWith("/") ? b + urlPart : b + "/" + urlPart;
        return resolved + jsonPart;
    }

    private static int findAppendedJsonStart(String url) {
        if (url == null) return -1;
        int depth = 0;
        for (int i = url.length() - 1; i >= 0; i--) {
            char c = url.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                depth--;
                if (depth == 0) {
                    int comma = i - 1;
                    while (comma >= 0 && url.charAt(comma) == ' ') comma--;
                    if (comma >= 0 && url.charAt(comma) == ',') return comma;
                }
            }
        }
        return -1;
    }
}
