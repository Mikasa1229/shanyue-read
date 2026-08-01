CREATE TABLE IF NOT EXISTS t_user_agent_preference (
    user_id BIGINT PRIMARY KEY,
    preferred_genres_json TEXT NOT NULL DEFAULT '[]',
    avoided_themes_json TEXT NOT NULL DEFAULT '[]',
    spoiler_level VARCHAR(16) NOT NULL DEFAULT 'STRICT',
    personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    retain_conversations BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
