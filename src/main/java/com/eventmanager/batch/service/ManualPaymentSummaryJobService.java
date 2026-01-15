package com.eventmanager.batch.service;

import com.eventmanager.batch.domain.BatchJobExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly aggregation job for fee-free manual payments.
 * Inserts daily snapshots into manual_payment_summary_report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualPaymentSummaryJobService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BatchJobExecutionService batchJobExecutionService;

    @Transactional
    public ManualPaymentSummaryStats runManualPaymentSummaryJob(
        String tenantId,
        Long eventId,
        LocalDate snapshotDate,
        String triggeredBy
    ) {
        LocalDate snapshot = snapshotDate != null ? snapshotDate : LocalDate.now(ZoneId.systemDefault());
        String resolvedTriggeredBy = triggeredBy != null ? triggeredBy : "SCHEDULED";

        BatchJobExecution execution = batchJobExecutionService.createJobExecution(
            "manualPaymentSummaryJob",
            "MANUAL_PAYMENT_SUMMARY",
            tenantId,
            resolvedTriggeredBy,
            String.format("{\"tenantId\":\"%s\",\"eventId\":%s,\"snapshotDate\":\"%s\"}",
                tenantId != null ? tenantId : "ALL",
                eventId != null ? eventId.toString() : "null",
                snapshot)
        );

        ManualPaymentSummaryStats stats = new ManualPaymentSummaryStats();
        stats.jobExecutionId = execution.getId();
        stats.snapshotDate = snapshot;
        stats.tenantId = tenantId;
        stats.eventId = eventId;

        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotDate", snapshot)
                .addValue("tenantId", tenantId)
                .addValue("eventId", eventId);

            int deleted = jdbcTemplate.update(
                "DELETE FROM manual_payment_summary_report " +
                    "WHERE snapshot_date = :snapshotDate " +
                    "AND (:tenantId IS NULL OR tenant_id = :tenantId) " +
                    "AND (:eventId IS NULL OR event_id = :eventId)",
                params
            );

            int inserted = jdbcTemplate.update(
                "INSERT INTO manual_payment_summary_report " +
                    "(tenant_id, event_id, payment_method_type, status, total_amount, transaction_count, snapshot_date, created_at) " +
                    "SELECT r.tenant_id, r.event_id, r.payment_method_type, r.status, " +
                    "       COALESCE(SUM(r.amount_due), 0), COUNT(*), :snapshotDate, now() " +
                    "FROM manual_payment_request r " +
                    "JOIN event_details e ON e.id = r.event_id " +
                    "WHERE r.event_id IS NOT NULL " +
                    "  AND e.manual_payment_enabled = true " +
                    "  AND (:tenantId IS NULL OR r.tenant_id = :tenantId) " +
                    "  AND (:eventId IS NULL OR r.event_id = :eventId) " +
                    "GROUP BY r.tenant_id, r.event_id, r.payment_method_type, r.status",
                params
            );

            stats.deletedRows = deleted;
            stats.insertedRows = inserted;

            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "COMPLETED",
                (long) inserted,
                (long) inserted,
                0L,
                null
            );

            log.info("Manual payment summary job completed. snapshotDate={}, tenantId={}, eventId={}, deletedRows={}, insertedRows={}",
                snapshot, tenantId, eventId, deleted, inserted);

            return stats;
        } catch (Exception e) {
            log.error("Manual payment summary job failed. snapshotDate={}, tenantId={}, eventId={}, error={}",
                snapshot, tenantId, eventId, e.getMessage(), e);

            batchJobExecutionService.completeJobExecution(
                execution.getId(),
                "FAILED",
                0L,
                0L,
                0L,
                e.getMessage()
            );

            throw e;
        }
    }

    public static class ManualPaymentSummaryStats {
        public Long jobExecutionId;
        public String tenantId;
        public Long eventId;
        public LocalDate snapshotDate;
        public int deletedRows;
        public int insertedRows;
    }
}
