package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SubjectTimelineDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private SubjectDTO subject;
    private List<SessionDTO> sessions;

    public SubjectTimelineDTO() {
        this.sessions = new ArrayList<>();
    }

    public SubjectDTO getSubject() {
        return subject;
    }

    public void setSubject(SubjectDTO subject) {
        this.subject = subject;
    }

    public List<SessionDTO> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionDTO> sessions) {
        this.sessions = sessions;
    }
}
