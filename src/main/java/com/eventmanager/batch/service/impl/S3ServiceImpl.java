package com.eventmanager.batch.service.impl;

import com.eventmanager.batch.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of S3Service for downloading HTML files.
 */
@Service
@Slf4j
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public S3ServiceImpl(
        @Value("${aws.s3.access-key}") String accessKey,
        @Value("${aws.s3.secret-key}") String secretKey,
        @Value("${aws.s3.region}") String region,
        @Value("${aws.s3.bucket-name:}") String bucketName
    ) {
        this.s3Client = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
        this.bucketName = bucketName;
    }

    @Override
    public String downloadHtmlFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            log.debug("URL is null or empty, returning empty string");
            return "";
        }

        try {
            // Extract S3 key from URL
            // URL format: https://bucket-name.s3.region.amazonaws.com/key or s3://bucket-name/key
            String s3Key = extractS3KeyFromUrl(url);
            String bucket = extractBucketFromUrl(url);

            if (s3Key == null || s3Key.isEmpty() || bucket == null || bucket.isEmpty()) {
                log.warn("Could not extract S3 key or bucket from URL: {}", url);
                return "";
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(response, StandardCharsets.UTF_8))) {

                StringBuilder htmlContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    htmlContent.append(line).append("\n");
                }

                log.debug("Downloaded HTML from S3: {}", url);
                return htmlContent.toString();
            }
        } catch (NoSuchKeyException e) {
            log.debug("HTML file not found in S3 (404): {}, returning empty string", url);
            return "";
        } catch (Exception e) {
            log.warn("Failed to download HTML from S3 URL {}: {}", url, e.getMessage());
            return "";
        }
    }

    private String extractS3KeyFromUrl(String url) {
        if (url.startsWith("s3://")) {
            // s3://bucket-name/key
            String withoutPrefix = url.substring(5);
            int firstSlash = withoutPrefix.indexOf('/');
            if (firstSlash > 0) {
                return withoutPrefix.substring(firstSlash + 1);
            }
        } else if (url.contains("amazonaws.com")) {
            // https://bucket-name.s3.region.amazonaws.com/key
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash > 0 && lastSlash < url.length() - 1) {
                return url.substring(lastSlash + 1);
            }
        } else if (url.contains("/")) {
            // Assume it's a relative path or key
            return url.startsWith("/") ? url.substring(1) : url;
        }
        return url;
    }

    private String extractBucketFromUrl(String url) {
        if (url.startsWith("s3://")) {
            // s3://bucket-name/key
            String withoutPrefix = url.substring(5);
            int firstSlash = withoutPrefix.indexOf('/');
            if (firstSlash > 0) {
                return withoutPrefix.substring(0, firstSlash);
            }
            return withoutPrefix;
        } else if (url.contains("amazonaws.com")) {
            // https://bucket-name.s3.region.amazonaws.com/key
            try {
                String host = new java.net.URL(url).getHost();
                if (host.contains(".")) {
                    return host.substring(0, host.indexOf('.'));
                }
            } catch (Exception e) {
                log.debug("Failed to parse URL: {}", url);
            }
        }
        // Fallback to configured bucket name
        return bucketName;
    }
}

