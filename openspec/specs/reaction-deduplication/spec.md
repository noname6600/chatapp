# reaction-deduplication Specification

## Purpose
Eliminate duplicate reactions from the same user on the same message by deduplicating reaction arrays in the store and during merge operations, ensuring clean and accurate reaction state.

## Requirements

### Requirement: Duplicate reactions are eliminated
The system SHALL deduplicate reactions keyed by (messageId, userId, emoji) tuple, keeping only one instance per unique tuple and removing any duplicates caused by optimistic and real updates coexisting.

#### Scenario: Optimistic and real reaction merge
- **WHEN** an optimistic reaction for (M1, U1, 🔥) exists in state and a real reaction event for the same tuple arrives
- **THEN** the optimistic copy is replaced by the real one, and no duplicate appears in the final state

#### Scenario: Multiple reactions deduplicated on merge
- **WHEN** a message M1 has multiple reactions including [🔥-U1-opt, 🔥-U1-real, ❤️-U1]
- **THEN** the deduplication process keeps only [🔥-U1-real, ❤️-U1] in the store

### Requirement: Real reactions take precedence over optimistic during deduplication
The system SHALL prefer backend-sourced reactions over optimistic ones when deduplicating, so the authoritative server state always wins conflicts.

#### Scenario: Real reaction replaces optimistic
- **WHEN** deduplicating (M1, U1, 🔥) and both optimistic and real versions exist
- **THEN** the real version (with backend-generated id and createdAt) is retained and optimistic is discarded

### Requirement: Deduplication preserves reaction count
The system SHALL ensure that reaction counts accurately reflect unique (messageId, userId, emoji) tuples after deduplication, without overcounting.

#### Scenario: Count reflects deduplicated reactions
- **WHEN** a message M1 shows 🔥 emoji with 2 identical reactions from U1 before dedup
- **THEN** after deduplication, 🔥 count is 1 for that user

### Requirement: Deduplication applies to all reaction operations
The system SHALL apply deduplication logic whenever reactions are updated: message fetch, optimistic add/remove, realtime merge, or store refresh.

#### Scenario: Dedup on message load
- **WHEN** a message is loaded from cache or API with duplicate reactions
- **THEN** the message is stored in deduplicated form

#### Scenario: Dedup on realtime update
- **WHEN** a reaction event merges into an existing message that has optimistic reactions
- **THEN** the merged result is deduplicated before being stored