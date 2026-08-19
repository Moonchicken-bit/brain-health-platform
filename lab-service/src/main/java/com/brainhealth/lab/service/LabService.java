package com.brainhealth.lab.service;
import com.brainhealth.common.model.PageResult;
import com.brainhealth.lab.entity.*;
import com.brainhealth.lab.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Collection;

@Service
public class LabService {
    private final LabResultRepository resultRepo;
    private final LabTestItemRepository testRepo;

    public LabService(LabResultRepository resultRepo, LabTestItemRepository testRepo) {
        this.resultRepo = resultRepo;
        this.testRepo = testRepo;
    }

    public PageResult<LabResult> listResults(Long sessionId, Long subjectId, Long labTestId, Boolean isAbnormal, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<LabResult> result;
        if (sessionId != null) result = resultRepo.findBySessionId(sessionId, pageable);
        else if (subjectId != null) result = resultRepo.findBySubjectId(subjectId, pageable);
        else if (labTestId != null) result = resultRepo.findByLabTestId(labTestId, pageable);
        else if (isAbnormal != null) result = resultRepo.findByIsAbnormal(isAbnormal, pageable);
        else result = resultRepo.findAll(pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public PageResult<LabResult> listResultsForSubjects(Collection<Long> subjectIds, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<LabResult> result = subjectIds.isEmpty()
            ? Page.empty(pageable) : resultRepo.findBySubjectIdIn(subjectIds, pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public LabResult getResult(Long id) { return resultRepo.findById(id).orElse(null); }

    @Transactional
    public LabResult createResult(LabResult r) { return resultRepo.save(r); }

    @Transactional
    public LabResult updateResult(Long id, LabResult data) {
        LabResult r = resultRepo.findById(id).orElseThrow(() -> new RuntimeException("Result not found"));
        if (data.getResult() != null) r.setResult(data.getResult());
        if (data.getUnit() != null) r.setUnit(data.getUnit());
        if (data.getReferenceRange() != null) r.setReferenceRange(data.getReferenceRange());
        if (data.getIsAbnormal() != null) r.setIsAbnormal(data.getIsAbnormal());
        if (data.getNotes() != null) r.setNotes(data.getNotes());
        return resultRepo.save(r);
    }

    @Transactional
    public void deleteResult(Long id) { resultRepo.deleteById(id); }

    @Transactional
    public List<LabResult> batchCreate(Long sessionId, List<LabResult> results) {
        results.forEach(r -> r.setSessionId(sessionId));
        return resultRepo.saveAll(results);
    }

    public List<LabTestItem> listTests(String category, String keyword) {
        if (category != null) return testRepo.findByCategory(category);
        if (keyword != null) return testRepo.findByNameContaining(keyword);
        return testRepo.findAll();
    }

    public LabTestItem getTest(Long id) { return testRepo.findById(id).orElse(null); }
}
