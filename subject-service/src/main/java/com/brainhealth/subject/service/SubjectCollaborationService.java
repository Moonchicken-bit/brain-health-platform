package com.brainhealth.subject.service;

import com.brainhealth.subject.entity.*;
import com.brainhealth.subject.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SubjectCollaborationService {
    private final SubjectRepository subjectRepository;
    private final SubjectFavoriteRepository favoriteRepository;
    private final SubjectBusinessTagRepository tagRepository;
    private final SubjectTagAssignmentRepository assignmentRepository;
    private final SubjectProjectNoteRepository noteRepository;

    public SubjectCollaborationService(
            SubjectRepository subjectRepository,
            SubjectFavoriteRepository favoriteRepository,
            SubjectBusinessTagRepository tagRepository,
            SubjectTagAssignmentRepository assignmentRepository,
            SubjectProjectNoteRepository noteRepository) {
        this.subjectRepository = subjectRepository;
        this.favoriteRepository = favoriteRepository;
        this.tagRepository = tagRepository;
        this.assignmentRepository = assignmentRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional
    public boolean setFavorite(Long userId, Long subjectId, boolean favorite) {
        requireSubject(subjectId);
        Optional<SubjectFavorite> existing = favoriteRepository.findByUserIdAndSubjectId(userId, subjectId);
        if (favorite && existing.isEmpty()) {
            SubjectFavorite value = new SubjectFavorite();
            value.setUserId(userId);
            value.setSubjectId(subjectId);
            favoriteRepository.save(value);
        } else if (!favorite) {
            existing.ifPresent(favoriteRepository::delete);
        }
        return favorite;
    }

    public Set<Long> favorites(Long userId, Long institutionId, Set<Long> projectIds, boolean admin) {
        Set<Long> result = new LinkedHashSet<>();
        List<Long> subjectIds = favoriteRepository.findByUserId(userId).stream()
            .map(SubjectFavorite::getSubjectId).toList();
        subjectRepository.findAllById(subjectIds).stream()
            .filter(subject -> admin || institutionId != null && institutionId.equals(subject.getInstitutionId())
                && projectIds != null && projectIds.contains(subject.getProjectId()))
            .forEach(subject -> result.add(subject.getId()));
        return result;
    }

    public void assertSubjectScope(Long subjectId, Long institutionId, Set<Long> projectIds, boolean admin) {
        if (admin) {
            requireSubject(subjectId);
            return;
        }
        Subject subject = requireSubject(subjectId);
        if (institutionId == null || !institutionId.equals(subject.getInstitutionId())
                || projectIds == null || !projectIds.contains(subject.getProjectId())) {
            throw new IllegalArgumentException("无权访问该受试者");
        }
    }

    public List<SubjectBusinessTag> listTags(Long projectId) {
        return tagRepository.findByProjectIdAndIsActiveTrueOrderByName(projectId);
    }

    @Transactional
    public SubjectBusinessTag createTag(Long userId, Long projectId, String name, String color) {
        if (projectId == null || projectId < 1) throw new IllegalArgumentException("项目不能为空");
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 50) throw new IllegalArgumentException("标签名称为 1-50 个字符");
        SubjectBusinessTag tag = new SubjectBusinessTag();
        tag.setProjectId(projectId);
        tag.setName(normalized);
        tag.setColor(color == null || color.isBlank() ? "#409EFF" : color.trim());
        tag.setCreatedBy(userId);
        return tagRepository.save(tag);
    }

    @Transactional
    public List<SubjectBusinessTag> setSubjectTags(Long userId, Long subjectId, List<Long> tagIds) {
        Subject subject = requireSubject(subjectId);
        Set<Long> requested = new LinkedHashSet<>(tagIds == null ? List.of() : tagIds);
        List<SubjectBusinessTag> tags = tagRepository.findAllById(requested);
        if (tags.size() != requested.size() || tags.stream().anyMatch(tag -> !tag.getProjectId().equals(subject.getProjectId()))) {
            throw new IllegalArgumentException("标签必须属于受试者所在项目");
        }
        Map<Long, SubjectTagAssignment> existing = new HashMap<>();
        assignmentRepository.findBySubjectId(subjectId).forEach(item -> existing.put(item.getTagId(), item));
        existing.values().stream().filter(item -> !requested.contains(item.getTagId()))
            .forEach(assignmentRepository::delete);
        requested.stream().filter(id -> !existing.containsKey(id)).forEach(tagId -> {
            SubjectTagAssignment item = new SubjectTagAssignment();
            item.setSubjectId(subjectId);
            item.setTagId(tagId);
            item.setCreatedBy(userId);
            assignmentRepository.save(item);
        });
        return tags;
    }

    public List<SubjectBusinessTag> subjectTags(Long subjectId) {
        requireSubject(subjectId);
        List<Long> ids = assignmentRepository.findBySubjectId(subjectId).stream()
            .map(SubjectTagAssignment::getTagId).toList();
        return tagRepository.findAllById(ids);
    }

    @Transactional
    public SubjectProjectNote saveNote(Long userId, Long subjectId, String content) {
        Subject subject = requireSubject(subjectId);
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty() || normalized.length() > 2000) {
            throw new IllegalArgumentException("项目备注为 1-2000 个字符");
        }
        int revision = noteRepository.findFirstBySubjectIdOrderByRevisionNoDesc(subjectId)
            .map(note -> note.getRevisionNo() + 1).orElse(1);
        SubjectProjectNote note = new SubjectProjectNote();
        note.setSubjectId(subjectId);
        note.setProjectId(subject.getProjectId());
        note.setRevisionNo(revision);
        note.setContent(normalized);
        note.setCreatedBy(userId);
        return noteRepository.save(note);
    }

    public List<SubjectProjectNote> noteHistory(Long subjectId) {
        requireSubject(subjectId);
        return noteRepository.findBySubjectIdOrderByRevisionNoDesc(subjectId);
    }

    private Subject requireSubject(Long subjectId) {
        return subjectRepository.findById(subjectId)
            .orElseThrow(() -> new IllegalArgumentException("受试者不存在"));
    }
}
