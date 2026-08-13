package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookSourceSeedResourceTest {

    @Test
    void seedContainsCompleteRulesWithUniqueIdsAndUrls() throws Exception {
        try (InputStream raw = getClass().getResourceAsStream(V19__seed_backup_book_sources.SEED_RESOURCE)) {
            assertNotNull(raw);
            JsonNode entries = new ObjectMapper().readTree(new GZIPInputStream(raw));
            assertEquals(1652, entries.size());

            Set<Long> ids = new HashSet<>();
            Set<String> urls = new HashSet<>();
            for (JsonNode entry : entries) {
                JsonNode rule = entry.path("rule");
                assertTrue(ids.add(entry.path("id").asLong()));
                assertTrue(urls.add(rule.path("bookSourceUrl").asText()));
                assertTrue(rule.path("searchUrl").isTextual());
                assertTrue(rule.path("ruleSearch").isObject());
            }
        }
    }
}
