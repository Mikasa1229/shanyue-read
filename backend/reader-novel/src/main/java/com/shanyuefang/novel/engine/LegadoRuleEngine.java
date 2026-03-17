package com.shanyuefang.novel.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化版 legado 规则引擎。
 *
 * <p>支持的规则类型：
 * <ul>
 *   <li>JSONPath：以 {@code $.} 开头，通过 Jayway JSONPath 解析</li>
 *   <li>XPath：以 {@code //} 开头，通过 Jsoup XPath 解析</li>
 *   <li>CSS 简写：{@code tag.class.N@attr}、{@code class.name@text} 等</li>
 *   <li>纯 CSS：传入 Jsoup CSS selector 查询</li>
 *   <li>正则：包含 {@code ##} 的规则</li>
 *   <li>链式：用 {@code @} 分隔多个操作</li>
 *   <li>降级：用 {@code ||} 分隔多个候选</li>
 * </ul>
 *
 * <p>不支持（返回 null）：{@code @js:} JavaScript 规则。
 */
@Slf4j
public class LegadoRuleEngine {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Configuration JP_CFG = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    // ─── 公开 API ─────────────────────────────────────────────

    /**
     * 从内容中按规则提取单个字符串值。
     *
     * @param rule  legado 规则字符串
     * @param input HTML 或 JSON 字符串
     * @return 提取结果；规则不支持或提取失败返回 null
     */
    public static String extractString(String rule, String input) {
        if (!StringUtils.hasText(rule) || !StringUtils.hasText(input)) return null;
        if (isJavaScript(rule)) return null;

        // 降级：|| 分隔
        for (String candidate : rule.split("\\|\\|")) {
            String r = candidate.trim();
            if (!StringUtils.hasText(r)) continue;
            String result = evalChain(r, input);
            if (StringUtils.hasText(result)) return result.trim();
        }
        return null;
    }

    /**
     * 从内容中按规则提取多个元素（用于列表规则，如 searchList、chapterList）。
     *
     * @param rule  列表规则字符串
     * @param input HTML 或 JSON 字符串
     * @return 元素列表（每个元素为 HTML 片段或 JSON 字符串），空列表表示未提取到
     */
    public static List<String> extractList(String rule, String input) {
        if (!StringUtils.hasText(rule) || !StringUtils.hasText(input)) return List.of();
        if (isJavaScript(rule)) return List.of();

        try {
            String trimmed = rule.trim();

            // JSONPath 列表
            if (trimmed.startsWith("$.") || trimmed.startsWith("$[")) {
                return jsonPathList(trimmed, input);
            }

            // XPath 列表（Jsoup）
            if (trimmed.startsWith("//")) {
                Document doc = Jsoup.parse(input);
                Elements els = doc.selectXpath(trimmed);
                List<String> result = new ArrayList<>();
                for (Element el : els) result.add(el.outerHtml());
                return result;
            }

            // @css: 前缀（legado 强制使用标准 CSS 选择器）
            String cssRule = trimmed.startsWith("@css:") ? trimmed.substring(5)
                           : trimmed.startsWith("css:")  ? trimmed.substring(4)
                           : trimmed;

            // CSS 列表（链式规则：先取容器再取子元素，如 class.txt-list.0@tag.li）
            // 对于含 @ 的规则，先取第一段作为容器，再用后续规则取子列表
            List<String> parts = splitByAt(cssRule);
            Document doc = Jsoup.parse(input);
            Elements els;
            if (parts.size() > 1) {
                // 先定位容器元素
                String containerRule = parts.get(0);
                Element container = selectSingle(doc, containerRule);
                if (container == null) return List.of();
                // 再按最后一段规则取子元素列表
                String childRule = parts.get(parts.size() - 1);
                els = container.select(toCssSelector(childRule));
            } else {
                els = doc.select(toCssSelector(cssRule));
            }
            List<String> result = new ArrayList<>();
            for (Element el : els) result.add(el.outerHtml());
            return result;

        } catch (Exception e) {
            log.debug("规则列表提取失败 rule={}: {}", rule, e.getMessage());
            return List.of();
        }
    }

    // ─── 核心计算 ─────────────────────────────────────────────

    /** 链式计算（@ 分隔） */
    private static String evalChain(String rule, String input) {
        List<String> parts = splitByAt(rule);
        String current = input;
        for (String part : parts) {
            if (!StringUtils.hasText(part)) continue;
            current = applyOp(part.trim(), current);
            if (current == null) return null;
        }
        return current;
    }

    /** 对单个操作求值 */
    private static String applyOp(String op, String input) {
        if (!StringUtils.hasText(op) || !StringUtils.hasText(input)) return null;
        if (isJavaScript(op)) return null;

        // 正则替换 ##pattern##replacement 或 ##pattern（仅提取）
        if (op.startsWith("##")) {
            return applyRegex(op.substring(2), input);
        }

        // @css: 前缀（legado 强制 CSS 选择器）
        if (op.startsWith("css:")) {
            return cssDirect(op.substring(4), input);
        }

        // JSONPath
        if (op.startsWith("$.") || op.startsWith("$[") || op.startsWith("$..")) {
            return jsonPathSingle(op, input);
        }

        // XPath（支持 //...）
        if (op.startsWith("//")) {
            return xpathSingle(op, input);
        }

        // 纯属性名（text/html/href/src/content 等）
        if (op.matches("[a-zA-Z][a-zA-Z0-9_-]*") && !op.contains(".")) {
            return extractAttr(input, op);
        }

        // legado CSS 简写 或 CSS selector
        return cssSingle(op, input);
    }

    // ─── JSONPath ────────────────────────────────────────────

    private static String jsonPathSingle(String path, String json) {
        try {
            Object val = JsonPath.using(JP_CFG).parse(json).read(path);
            if (val == null) return null;
            if (val instanceof List<?> list) {
                return list.isEmpty() ? null : String.valueOf(list.get(0));
            }
            return String.valueOf(val);
        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("JSONPath 提取失败 path={}: {}", path, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> jsonPathList(String path, String json) {
        try {
            Object val = JsonPath.using(JP_CFG).parse(json).read(path);
            if (val == null) return List.of();
            List<Object> raw = (val instanceof List) ? (List<Object>) val : List.of(val);
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item == null) continue;
                // 保留 JSON 字符串，方便后续对每个 item 再用 JSONPath 规则提取
                result.add(item instanceof String ? (String) item : OM.writeValueAsString(item));
            }
            return result;
        } catch (Exception e) {
            log.debug("JSONPath 列表提取失败 path={}: {}", path, e.getMessage());
            return List.of();
        }
    }

    // ─── XPath ──────────────────────────────────────────────

    private static String xpathSingle(String xpath, String html) {
        try {
            Document doc = Jsoup.parse(html);
            Elements els = doc.selectXpath(xpath);
            if (els.isEmpty()) return null;
            Element el = els.first();
            // XPath 末尾有 /@attr 时 Jsoup selectXpath 返回的是 attr value 作为 text
            return el.ownText().isBlank() ? el.text() : el.ownText();
        } catch (Exception e) {
            log.debug("XPath 提取失败 xpath={}: {}", xpath, e.getMessage());
            return null;
        }
    }

    // ─── CSS ────────────────────────────────────────────────

    /**
     * 将 legado CSS 简写转换为属性提取结果。
     * 格式：{@code selector.N@attr} 或 {@code selector@attr}
     *   - selector 可以是 legado 简写（如 {@code class.bookname}）或标准 CSS
     *   - N 是可选的索引
     *   - attr 是 text/html/href/src/content/... 或空（默认 text）
     */
    private static String cssSingle(String rule, String input) {
        try {
            // 分离末尾的 @attr 或 @attr##regex（如 class.foo@html##pattern##replace）
            String selector;
            String attr = "text";
            String regexSuffix = null;
            int lastAt = rule.lastIndexOf('@');
            if (lastAt > 0 && lastAt < rule.length() - 1) {
                String afterAt = rule.substring(lastAt + 1);
                // 允许 @attrName 或 @attrName##regex
                int hashIdx = afterAt.indexOf("##");
                String attrPart = hashIdx >= 0 ? afterAt.substring(0, hashIdx) : afterAt;
                if (attrPart.matches("[a-zA-Z][a-zA-Z0-9_-]*")) {
                    selector = rule.substring(0, lastAt);
                    attr = attrPart;
                    if (hashIdx >= 0) regexSuffix = afterAt.substring(hashIdx + 2);
                } else {
                    selector = rule;
                }
            } else {
                selector = rule;
            }

            // 提取可能的索引（.N 结尾）
            int index = -1;
            Matcher idxM = Pattern.compile("\\.(\\d+)$").matcher(selector);
            if (idxM.find()) {
                index = Integer.parseInt(idxM.group(1));
                selector = selector.substring(0, idxM.start());
            }

            String cssSelector = toCssSelector(selector);

            Document doc = Jsoup.parse(input);
            Elements els = doc.select(cssSelector);
            if (els.isEmpty()) return null;

            Element el = (index >= 0 && index < els.size()) ? els.get(index) : els.first();
            String result = extractAttr(el, attr);
            if (result != null && regexSuffix != null) {
                result = applyRegex(regexSuffix, result);
            }
            return result;

        } catch (Exception e) {
            log.debug("CSS 提取失败 rule={}: {}", rule, e.getMessage());
            return null;
        }
    }

    /** 直接用标准 CSS selector 查询（不解析 legado 简写），提取第一个匹配元素的文本 */
    private static String cssDirect(String cssSelector, String input) {
        try {
            Document doc = Jsoup.parse(input);
            Elements els = doc.select(cssSelector);
            if (els.isEmpty()) return null;
            return els.first().text();
        } catch (Exception e) {
            log.debug("CSS 直接查询失败 selector={}: {}", cssSelector, e.getMessage());
            return null;
        }
    }

    /** 从 Document 中按 legado CSS shorthand 规则选取单个元素（支持 .N 索引） */
    private static Element selectSingle(Document doc, String rule) {
        try {
            int index = 0;
            String sel = rule;
            Matcher idxM = Pattern.compile("\\.(\\d+)$").matcher(sel);
            if (idxM.find()) {
                index = Integer.parseInt(idxM.group(1));
                sel = sel.substring(0, idxM.start());
            }
            String cssSelector = toCssSelector(sel);
            Elements els = doc.select(cssSelector);
            if (els.isEmpty()) return null;
            return (index < els.size()) ? els.get(index) : els.first();
        } catch (Exception e) {
            log.debug("selectSingle 失败 rule={}: {}", rule, e.getMessage());
            return null;
        }
    }

    /** 将 legado CSS 简写转换为标准 CSS selector */
    private static String toCssSelector(String rule) {
        if (!StringUtils.hasText(rule)) return "*";
        // 去掉末尾数字索引
        String sel = rule.replaceAll("\\.(\\d+)$", "");
        // class.xxx → .xxx
        sel = sel.replaceAll("^class\\.", ".");
        // id.xxx → #xxx
        sel = sel.replaceAll("^id\\.", "#");
        // tag.xxx → xxx（保持不变）
        sel = sel.replaceAll("^tag\\.", "");
        return sel.isEmpty() ? "*" : sel;
    }

    /** 从元素中按属性名提取值 */
    private static String extractAttr(Element el, String attr) {
        if (el == null) return null;
        return switch (attr.toLowerCase()) {
            case "text" -> el.text();
            case "owntext" -> el.ownText();
            case "html", "innerhtml" -> el.html();
            case "outerhtml" -> el.outerHtml();
            case "href" -> el.absUrl("href").isEmpty() ? el.attr("href") : el.absUrl("href");
            case "src" -> el.absUrl("src").isEmpty() ? el.attr("src") : el.absUrl("src");
            default -> {
                String val = el.attr(attr);
                yield val.isEmpty() ? el.text() : val;
            }
        };
    }

    /** 从 HTML 字符串中按属性名提取值（当 input 是 HTML 片段时） */
    private static String extractAttr(String html, String attr) {
        try {
            Element el = Jsoup.parse(html).body();
            // 如果只有一个子元素，用子元素
            if (el.children().size() == 1) el = el.child(0);
            return extractAttr(el, attr);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── 正则 ────────────────────────────────────────────────

    /**
     * 处理正则规则（去掉前缀 ## 后传入）。
     * <ul>
     *   <li>{@code pattern} → 提取第一个匹配组（或整个匹配）</li>
     *   <li>{@code pattern##replacement} → 替换</li>
     * </ul>
     */
    private static String applyRegex(String ruleBody, String input) {
        try {
            String[] parts = ruleBody.split("##", 2);
            String pattern = parts[0];
            if (parts.length == 1) {
                // 仅提取
                Matcher m = Pattern.compile(pattern, Pattern.DOTALL).matcher(input);
                if (!m.find()) return null;
                return m.groupCount() > 0 ? m.group(1) : m.group();
            } else {
                // 替换
                return input.replaceAll(pattern, parts[1]);
            }
        } catch (Exception e) {
            log.debug("正则规则处理失败 rule={}: {}", ruleBody, e.getMessage());
            return null;
        }
    }

    // ─── 工具方法 ────────────────────────────────────────────

    /**
     * 按 @ 分割规则链。
     * <ul>
     *   <li>{@code @js:} → JavaScript，追加到当前段并结束</li>
     *   <li>{@code @attrName}（纯单词，直到下一个 @ 或字符串末尾）→ 属性选择器，不分割</li>
     *   <li>其余 @ → 链式分割符</li>
     * </ul>
     */
    private static List<String> splitByAt(String rule) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < rule.length(); i++) {
            char c = rule.charAt(i);
            if (c == '@') {
                String rest = rule.substring(i + 1);
                // @js: → JavaScript，停止分割
                if (rest.startsWith("js:") || rest.startsWith("js\n")) {
                    cur.append(rule.substring(i));
                    break;
                }
                // 判断 @ 后面到下一个 @ 为止是否是属性名（如 text/href/title/data-src 等）
                // 支持 @attrName##regex 格式（如 @html##pattern）
                int nextAt = rest.indexOf('@');
                String segment = nextAt >= 0 ? rest.substring(0, nextAt) : rest;
                // 属性部分：## 之前的内容
                String attrPart = segment.contains("##") ? segment.substring(0, segment.indexOf("##")) : segment;
                if (attrPart.matches("[a-zA-Z][a-zA-Z0-9_-]*")) {
                    // 属性选择器（如 @text、@href、@html##regex），不分割，原样保留
                    cur.append(c);
                } else {
                    // 链式分割符
                    if (cur.length() > 0) {
                        parts.add(cur.toString());
                        cur = new StringBuilder();
                    }
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts;
    }

    private static boolean isJavaScript(String rule) {
        return rule.startsWith("@js:") || rule.startsWith("@js\n")
                || rule.contains("<js>") || rule.contains("@js:");
    }
}
