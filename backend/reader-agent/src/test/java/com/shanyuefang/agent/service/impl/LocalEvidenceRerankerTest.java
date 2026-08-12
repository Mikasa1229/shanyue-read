package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.service.RerankerService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEvidenceRerankerTest {
    @Test
    void combinesLexicalEvidenceAndCrossSourceCorroboration() {
        RerankerService.Candidate exact = new RerankerService.Candidate(
                "[Chapter 3] Lin Daiyu discovers a hidden clue and shares it with her companion.", 0.50D,
                "POSTGRESQL,ELASTICSEARCH,MILVUS");
        RerankerService.Candidate semanticOnly = new RerankerService.Candidate(
                "[Chapter 3] A meeting ends in the night and everyone leaves.", 0.60D, "MILVUS");

        List<RerankerService.Candidate> result = LocalEvidenceReranker.rank(
                "Lin Daiyu hidden clue", List.of(semanticOnly, exact), 2);

        assertThat(result).extracting(RerankerService.Candidate::content)
                .containsExactly(exact.content(), semanticOnly.content());
    }

    @Test
    void keepsSemanticOrderWhenTheQueryHasNoLexicalTerms() {
        RerankerService.Candidate high = new RerankerService.Candidate("[Chapter 1] A", 0.9D, "MILVUS");
        RerankerService.Candidate low = new RerankerService.Candidate("[Chapter 1] B", 0.1D, "POSTGRESQL");

        assertThat(LocalEvidenceReranker.rank("?", List.of(low, high), 1))
                .extracting(RerankerService.Candidate::content).containsExactly(high.content());
    }

    @Test
    void remainsDeterministicAndBoundedAcrossRepeatedRuns() {
        List<RerankerService.Candidate> candidates = List.of(
                new RerankerService.Candidate("[Chapter 2] Lin Daiyu discovers a clue.", 0.42D, "MILVUS"),
                new RerankerService.Candidate("[Chapter 2] Lin Daiyu shares the clue with a companion.", 0.38D,
                        "POSTGRESQL,ELASTICSEARCH"),
                new RerankerService.Candidate("[Chapter 3] Everyone leaves in the night.", 0.81D, "MILVUS"));

        List<String> expected = LocalEvidenceReranker.rank("Lin Daiyu clue", candidates, 3).stream()
                .map(RerankerService.Candidate::content).toList();
        for (int run = 0; run < 50; run++) {
            List<RerankerService.Candidate> ranked = LocalEvidenceReranker.rank("Lin Daiyu clue", candidates, 3);
            assertThat(ranked).hasSize(3).doesNotHaveDuplicates();
            assertThat(ranked.stream().map(RerankerService.Candidate::content).toList())
                    .containsExactlyElementsOf(expected);
        }
    }
}
