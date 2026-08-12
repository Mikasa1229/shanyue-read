package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.agent.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalEvaluationControllerTest {
    @Test
    void rejectsRetrievalBeforeReadingKnowledgeWhenInternalTokenIsInvalid() {
        AgentInternalAccess access = mock(AgentInternalAccess.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        doThrow(new IllegalArgumentException("invalid token")).when(access).require("bad-token");
        InternalEvaluationController controller = new InternalEvaluationController(access, knowledge);

        assertThrows(IllegalArgumentException.class,
                () -> controller.retrieve("bad-token", 8L, 99, "陈平安住在哪里", 5));

        verify(knowledge, never()).retrieveDetailed(8L, 99, "陈平安住在哪里", 5, 0L);
    }
}
