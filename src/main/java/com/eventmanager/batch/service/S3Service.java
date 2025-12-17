package com.eventmanager.batch.service;

/**
 * Service for S3 operations.
 * Simplified version for batch job processing - only downloads HTML files.
 */
public interface S3Service {
    /**
     * Download an HTML file from S3 by its URL and return its content as a String.
     *
     * @param url the S3 file URL.
     * @return the HTML content as a String, or empty string if not found.
     */
    String downloadHtmlFromUrl(String url);

    /**
     * Download an HTML file from S3 with retry logic.
     * Attempts to download with exponential backoff retries.
     *
     * @param url the S3 file URL.
     * @param maxRetries maximum number of retry attempts.
     * @param initialDelayMs initial delay between retries in milliseconds.
     * @return the HTML content as a String, or empty string if not found after all retries.
     */
    String downloadHtmlFromUrlWithRetry(String url, int maxRetries, long initialDelayMs);
}

