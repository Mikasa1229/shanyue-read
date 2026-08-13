package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Seeds the full optional Legado catalog without embedding megabytes of SQL. */
public class V19__seed_backup_book_sources extends BaseJavaMigration {

    static final String SEED_RESOURCE = "/db/seed/legado-sources-v1.json.gz";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> CURATED_DEFAULT_URLS = Set.of(
            "https://www.360tingshu.cc", "http://www.66story.com", "http://m.qudushu.com",
            "http://www.xbotaodz.com", "http://www.booksky.cc", "https://wap.xyushuwu11.com",
            "http://wap.wangshuge.la", "http://www.mxjtedu.com/", "https://www.489d.com",
            "https://www.dcrbk.com", "https://www.conglianhao.com", "https://www.biquge7.top/",
            "https://www.biquge7.top", "http://www.bbiquge8.net", "https://www.biquge99.cc/",
            "https://www.biquge7.xyz", "https://wap.jhssd.com", "https://www.jhssd.com",
            "http://wap.wangshuge.info", "http://m.xhytd.com"
    );

    @Override
    public void migrate(Context context) throws Exception {
        try (InputStream raw = getClass().getResourceAsStream(SEED_RESOURCE)) {
            if (raw == null) throw new IllegalStateException("Missing book-source seed: " + SEED_RESOURCE);
            try (GZIPInputStream gzip = new GZIPInputStream(raw);
                 PreparedStatement statement = context.getConnection().prepareStatement("""
                         INSERT INTO t_book_source
                             (id, source_name, source_url, source_type, source_group, enabled, source_json, created_at, updated_at)
                         VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                         ON CONFLICT (source_url) DO UPDATE SET
                             source_name = EXCLUDED.source_name,
                             source_type = EXCLUDED.source_type,
                             source_group = EXCLUDED.source_group,
                             source_json = EXCLUDED.source_json,
                             updated_at = NOW()
                         """)) {
                JsonNode entries = OBJECT_MAPPER.readTree(gzip);
                if (!entries.isArray()) throw new IllegalStateException("Book-source seed must be a JSON array");
                for (JsonNode entry : entries) {
                    JsonNode rule = entry.path("rule");
                    String url = requiredText(rule, "bookSourceUrl");
                    statement.setLong(1, entry.path("id").asLong());
                    statement.setString(2, requiredText(rule, "bookSourceName"));
                    statement.setString(3, url);
                    statement.setInt(4, rule.path("bookSourceType").asInt(0));
                    statement.setString(5, nullableText(rule, "bookSourceGroup"));
                    statement.setBoolean(6, CURATED_DEFAULT_URLS.contains(url));
                    statement.setString(7, OBJECT_MAPPER.writeValueAsString(rule));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) throw new IllegalStateException("Seed entry is missing " + field);
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
