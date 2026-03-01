package com.eventmanager.batch.job.refund.reader;

import com.eventmanager.batch.domain.EventTicketTransaction;
import com.eventmanager.batch.repository.EventTicketTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;

/**
 * Reader for Stripe Ticket Batch Refund Job.
 * Reads eligible tickets from database for refund processing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EligibleTicketReader implements ItemReader<EventTicketTransaction> {

    private final EventTicketTransactionRepository repository;

    private Long eventId;
    private String tenantId;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private Iterator<EventTicketTransaction> ticketIterator;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 100;
    private boolean hasMorePages = true;

    /**
     * Initialize reader with job parameters.
     */
    public void initialize(Long eventId, String tenantId, ZonedDateTime startDate, ZonedDateTime endDate) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ticketIterator = null;
        this.currentPage = 0;
        this.hasMorePages = true;
        log.info("Initialized EligibleTicketReader - eventId: {}, tenantId: {}, startDate: {}, endDate: {}",
            eventId, tenantId, startDate, endDate);
    }

    @Override
    public EventTicketTransaction read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (ticketIterator == null || !ticketIterator.hasNext()) {
            if (!hasMorePages) {
                return null; // End of data
            }

            // Load next page
            List<EventTicketTransaction> tickets = loadNextPage();
            if (tickets == null || tickets.isEmpty()) {
                hasMorePages = false;
                return null; // No more tickets
            }

            ticketIterator = tickets.iterator();
            log.debug("Loaded page {} with {} tickets", currentPage, tickets.size());
        }

        if (ticketIterator.hasNext()) {
            return ticketIterator.next();
        }

        return null;
    }

    /**
     * Load next page of eligible tickets.
     */
    private List<EventTicketTransaction> loadNextPage() {
        try {
            Pageable pageable = PageRequest.of(currentPage, PAGE_SIZE);

            // Convert ZonedDateTime to Timestamp for native query
            Timestamp startTimestamp = startDate != null ? Timestamp.from(startDate.toInstant()) : null;
            Timestamp endTimestamp = endDate != null ? Timestamp.from(endDate.toInstant()) : null;

            Page<EventTicketTransaction> page = repository.findEligibleTicketsForRefund(
                eventId, tenantId, startTimestamp, endTimestamp, pageable
            );

            if (page.isEmpty()) {
                hasMorePages = false;
                return List.of();
            }

            hasMorePages = page.hasNext();
            currentPage++;

            return page.getContent();
        } catch (Exception e) {
            log.error("Error loading tickets for refund - eventId: {}, tenantId: {}: {}",
                eventId, tenantId, e.getMessage(), e);
            hasMorePages = false;
            return List.of();
        }
    }
}
