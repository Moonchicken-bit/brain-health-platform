package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single entry in a subject's timeline.
 * Represents a chronologically ordered event (registration, session, etc.).
 */
public class TimelineItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Type of timeline event: REGISTRATION, SESSION, ASSESSMENT, etc. */
    private String eventType;

    /** Human-readable label for display on the timeline. */
    private String label;

    /** Date/time when this event occurred. */
    private LocalDateTime eventDate;

    /** Date portion (for session events). */
    private LocalDate sessionDate;

    /** Additional description or status. */
    private String description;

    /** Related entity id (session id, assessment id, etc.). */
    private Long referenceId;

    /** Sequence number within the timeline. */
    private Integer sequence;

    /** Days elapsed from the previous event. */
    private Long daysSincePrevious;

    /** Days elapsed from the baseline (first) event. */
    private Long daysFromBaseline;

    public TimelineItemDTO() {}

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Long getDaysSincePrevious() {
        return daysSincePrevious;
    }

    public void setDaysSincePrevious(Long daysSincePrevious) {
        this.daysSincePrevious = daysSincePrevious;
    }

    public Long getDaysFromBaseline() {
        return daysFromBaseline;
    }

    public void setDaysFromBaseline(Long daysFromBaseline) {
        this.daysFromBaseline = daysFromBaseline;
    }
}
