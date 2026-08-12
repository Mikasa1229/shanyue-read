package com.shanyuefang.agent.service;

/** Reports durable graph-build milestones without coupling knowledge construction to task storage. */
public interface GraphBuildProgressListener {
    GraphBuildProgressListener NOOP = new GraphBuildProgressListener() { };

    default void chapterExtracted(int completedChapters) { }

    default void stageStarted(Stage stage) { }

    default void stageCompleted(Stage stage) { }

    /** Completes a stage while preserving the real number of work units reported for it. */
    default void stageCompleted(Stage stage, int totalUnits) {
        stageCompleted(stage);
    }

    default void stageProgress(Stage stage, int completedUnits, int totalUnits) { }

    enum Stage {
        EXTRACT,
        CHARACTER_CALIBRATION,
        STORY_EVENTS,
        CLUE_SYNTHESIS,
        CLUE_LIFECYCLE,
        RAG_REFRESH,
        GRAPH_PROJECTION,
        FINALIZE
    }
}
