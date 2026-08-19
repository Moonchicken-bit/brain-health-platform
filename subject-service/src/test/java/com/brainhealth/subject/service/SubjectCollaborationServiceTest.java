package com.brainhealth.subject.service;

import com.brainhealth.subject.entity.*;
import com.brainhealth.subject.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubjectCollaborationServiceTest {
    @Mock SubjectRepository subjectRepository;
    @Mock SubjectFavoriteRepository favoriteRepository;
    @Mock SubjectBusinessTagRepository tagRepository;
    @Mock SubjectTagAssignmentRepository assignmentRepository;
    @Mock SubjectProjectNoteRepository noteRepository;
    SubjectCollaborationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SubjectCollaborationService(subjectRepository, favoriteRepository,
            tagRepository, assignmentRepository, noteRepository);
        Subject subject = new Subject();
        subject.setId(10L);
        subject.setProjectId(20L);
        when(subjectRepository.findById(10L)).thenReturn(Optional.of(subject));
    }

    @Test
    void favoriteIsStoredForAuthenticatedUserOnly() {
        when(favoriteRepository.findByUserIdAndSubjectId(7L, 10L)).thenReturn(Optional.empty());
        assertTrue(service.setFavorite(7L, 10L, true));
        verify(favoriteRepository).save(argThat(value ->
            value.getUserId().equals(7L) && value.getSubjectId().equals(10L)));
    }

    @Test
    void rejectsTagFromAnotherProject() {
        SubjectBusinessTag foreign = new SubjectBusinessTag();
        foreign.setProjectId(99L);
        when(tagRepository.findAllById(Set.of(3L))).thenReturn(List.of(foreign));
        assertThrows(IllegalArgumentException.class,
            () -> service.setSubjectTags(7L, 10L, List.of(3L)));
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void noteEditCreatesNewRevisionInsteadOfOverwriting() {
        SubjectProjectNote previous = new SubjectProjectNote();
        previous.setRevisionNo(2);
        when(noteRepository.findFirstBySubjectIdOrderByRevisionNoDesc(10L))
            .thenReturn(Optional.of(previous));
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectProjectNote saved = service.saveNote(7L, 10L, "需要复核异常结果");
        assertEquals(3, saved.getRevisionNo());
        assertEquals(20L, saved.getProjectId());
        assertEquals(7L, saved.getCreatedBy());
    }
}
