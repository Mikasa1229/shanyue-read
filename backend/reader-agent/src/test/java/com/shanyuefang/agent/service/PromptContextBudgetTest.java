package com.shanyuefang.agent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptContextBudgetTest {
    @Test
    void preservesEvidenceAndRejectsLaterHistoryAfterTheSharedBudgetIsConsumed() {
        PromptContextBudget budget = new PromptContextBudget(10);

        assertTrue(budget.add("evidence", "1234567890123456789012345678901234567890"));
        assertFalse(budget.add("history", "later conversation must not displace evidence"));

        assertEquals(10, budget.totalTokens());
        assertEquals(10, budget.tokens("evidence"));
        assertEquals(0, budget.tokens("history"));
    }
}
