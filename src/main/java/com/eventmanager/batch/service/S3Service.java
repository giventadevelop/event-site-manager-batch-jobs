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
}

