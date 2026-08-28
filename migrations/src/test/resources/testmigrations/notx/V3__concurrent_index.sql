-- octavius:no-transaction
-- CREATE INDEX CONCURRENTLY is refused inside a transaction block.
CREATE INDEX CONCURRENTLY idx_castra_id ON castra (id);
