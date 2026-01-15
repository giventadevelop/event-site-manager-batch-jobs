package com.eventmanager.batch.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for synchronizing the sequence_generator sequence with the maximum IDs
 * across all tables that use it. Dynamically queries tables that have an 'id' column
 * to avoid errors when tables don't have this column.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceSynchronizationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Synchronizes the sequence_generator sequence to be at least as high as
     * the maximum ID across all tables that use it and have a numeric 'id' column.
     * Only includes tables with numeric id columns (bigint, integer, etc.) to avoid
     * type mismatch errors with text/varchar id columns.
     *
     * @return the new sequence value that was set
     */
    @Transactional
    public Long synchronizeSequence() {
        log.info("Synchronizing sequence_generator with maximum IDs across all tables...");

        // First, find all tables in public schema that have a numeric 'id' column
        // Only include numeric types (bigint, integer, int8, int4, etc.) to avoid type mismatch errors
        String findTablesSql = """
            SELECT table_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND column_name = 'id'
              AND table_name NOT LIKE 'BATCH_%'
              AND data_type IN ('bigint', 'integer', 'int8', 'int4', 'smallint', 'int2')
            ORDER BY table_name
            """;

        try {
            Query findTablesQuery = entityManager.createNativeQuery(findTablesSql);
            @SuppressWarnings("unchecked")
            List<String> tablesWithId = (List<String>) findTablesQuery.getResultList();

            if (tablesWithId.isEmpty()) {
                log.warn("No tables with numeric 'id' column found. Setting sequence to minimum value 1.");
                try {
                    Query setMinQuery = entityManager.createNativeQuery(
                        "SELECT setval('public.sequence_generator', 1, true)"
                    );
                    Object result = setMinQuery.getSingleResult();
                    return result != null ? ((Number) result).longValue() : 1L;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("permission denied")) {
                        log.warn("Permission denied for sequence synchronization. Skipping.");
                        return null;
                    }
                    throw e;
                }
            }

            log.debug("Found {} tables with numeric 'id' column: {}", tablesWithId.size(), tablesWithId);

            // Build the GREATEST expression dynamically
            // Cast MAX(id) to BIGINT to ensure type consistency across all tables
            // Using CAST() instead of :: to avoid Hibernate parameter placeholder conflicts
            String maxIdExpressions = tablesWithId.stream()
                .map(tableName -> String.format("COALESCE((SELECT CAST(MAX(id) AS bigint) FROM public.%s), CAST(0 AS bigint))", tableName))
                .collect(Collectors.joining(",\n                    "));

            // Try setval() without pg_catalog prefix first (works if user has UPDATE privilege)
            String sql = String.format("""
                SELECT setval(
                    'public.sequence_generator',
                    GREATEST(
                        %s,
                        CAST(1 AS bigint)
                    ),
                    true
                )
                """, maxIdExpressions);

            log.debug("Executing sequence synchronization query for {} tables", tablesWithId.size());

            Query query = entityManager.createNativeQuery(sql);
            Object result = query.getSingleResult();
            Long newSequenceValue = result != null ? ((Number) result).longValue() : null;

            log.info("Sequence synchronized successfully. New sequence value: {} (checked {} tables)",
                newSequenceValue, tablesWithId.size());

            // Verify the synchronization
            Query verifyQuery = entityManager.createNativeQuery("SELECT last_value FROM public.sequence_generator");
            Object verifyResult = verifyQuery.getSingleResult();
            Long actualSequenceValue = verifyResult != null ? ((Number) verifyResult).longValue() : null;

            log.debug("Verified sequence value: {}", actualSequenceValue);

            return actualSequenceValue;
        } catch (Exception e) {
            // Check if it's a permission error - if so, log warning and return null
            // The AOP aspect will handle duplicate key violations if they occur
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("permission denied")) {
                log.warn("Permission denied for sequence synchronization. " +
                    "Database user lacks UPDATE privilege on sequence_generator. " +
                    "Pre-synchronization skipped. AOP aspect will handle duplicate key violations if they occur.");
                return null;
            }
            log.error("Failed to synchronize sequence_generator", e);
            throw new RuntimeException("Failed to synchronize sequence_generator: " + errorMessage, e);
        }
    }
}
