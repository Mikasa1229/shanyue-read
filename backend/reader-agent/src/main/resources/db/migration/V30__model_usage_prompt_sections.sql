-- Aggregate prompt section sizes are privacy-safe cost evidence; prompt text is never stored here.
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS system_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS history_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS graph_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS community_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS evidence_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_model_usage ADD COLUMN IF NOT EXISTS tool_tokens INTEGER NOT NULL DEFAULT 0;
