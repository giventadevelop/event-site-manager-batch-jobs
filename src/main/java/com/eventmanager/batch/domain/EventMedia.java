package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Entity for event_media.
 * Simplified version for batch job processing.
 * Includes event_focus_group_id for focus group–scoped media (see backend PRD).
 */
@Entity
@Table(name = "event_media")
@Data
public class EventMedia implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "event_media_type", length = 255, nullable = false)
    private String eventMediaType;

    @Column(name = "storage_type", length = 255, nullable = false)
    private String storageType;

    @Column(name = "file_url", length = 2048)
    private String fileUrl;

    @Column(name = "file_data_content_type", length = 255)
    private String fileDataContentType;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "event_flyer")
    private Boolean eventFlyer;

    @Column(name = "is_email_header_image")
    private Boolean isEmailHeaderImage;

    @Column(name = "is_event_management_official_document")
    private Boolean isEventManagementOfficialDocument;

    @Column(name = "pre_signed_url", length = 2048)
    private String preSignedUrl;

    @Column(name = "pre_signed_url_expires_at")
    private ZonedDateTime preSignedUrlExpiresAt;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "download_count")
    private Integer downloadCount;

    @Column(name = "is_featured_video")
    private Boolean isFeaturedVideo;

    @Column(name = "featured_video_url", length = 2048)
    private String featuredVideoUrl;

    @Column(name = "is_hero_image")
    private Boolean isHeroImage;

    @Column(name = "is_active_hero_image")
    private Boolean isActiveHeroImage;

    @Column(name = "start_displaying_from_date", nullable = false)
    private LocalDate startDisplayingFromDate;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "uploaded_by_id")
    private Long uploadedById;

    @Column(name = "sponsor_id")
    private Long sponsorId;

    @Column(name = "event_sponsors_join_id")
    private Long eventSponsorsJoinId;

    @Column(name = "event_focus_group_id")
    private Long eventFocusGroupId;

    @Column(name = "performer_id")
    private Long performerId;

    @Column(name = "director_id")
    private Long directorId;

    @Column(name = "priority_ranking", nullable = false)
    private Integer priorityRanking = 0;

    @Column(name = "is_home_page_hero_image", nullable = false)
    private boolean isHomePageHeroImage = false;

    @Column(name = "is_featured_event_image", nullable = false)
    private boolean isFeaturedEventImage = false;

    @Column(name = "is_live_event_image", nullable = false)
    private boolean isLiveEventImage = false;
}
