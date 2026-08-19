package com.brainhealth.adni.controller;
import com.brainhealth.adni.entity.AdniSubject;
import com.brainhealth.adni.repository.AdniSubjectRepository;
import com.brainhealth.adni.service.AdniImportService;
import com.brainhealth.common.model.ApiResponse;
import com.brainhealth.common.model.PageResult;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/adni")
public class AdniController {
    private final AdniSubjectRepository repo;
    private final AdniImportService importService;
    public AdniController(AdniSubjectRepository repo, AdniImportService importService) {
        this.repo = repo;
        this.importService = importService;
    }

    @GetMapping("/subjects")
    public ApiResponse<PageResult<AdniSubject>> listSubjects(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String diagnosis, @RequestParam(required = false) String sex,
            @RequestParam(required = false) String apoeStatus, @RequestParam(required = false) Integer ageMin,
            @RequestParam(required = false) Integer ageMax) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<AdniSubject> result;
        if (diagnosis != null) result = repo.findByDiagnosis(diagnosis, pageable);
        else if (sex != null) result = repo.findBySex(sex, pageable);
        else if (apoeStatus != null) result = repo.findByApoeGenotype(apoeStatus, pageable);
        else if (ageMin != null && ageMax != null) result = repo.findByAgeBetween(ageMin, ageMax, pageable);
        else result = repo.findAll(pageable);
        return ApiResponse.ok(PageResult.of(page, size, result.getTotalElements(), result.getContent()));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSubjects", repo.count());
        stats.put("cnCount", repo.findByDiagnosis("CN", Pageable.unpaged()).getTotalElements());
        stats.put("mciCount", repo.findByDiagnosis("MCI", Pageable.unpaged()).getTotalElements());
        stats.put("adCount", repo.findByDiagnosis("AD", Pageable.unpaged()).getTotalElements());
        stats.put("otherCount", repo.findByDiagnosis("Other", Pageable.unpaged()).getTotalElements());
        return ApiResponse.ok(stats);
    }

    @PostMapping("/subjects/{adniSubjectId}/link")
    public ApiResponse<Map<String, String>> linkToLocalSubject(@PathVariable Long adniSubjectId, @RequestBody Map<String, Object> body) {
        AdniSubject s = repo.findById(adniSubjectId).orElseThrow();
        s.setLocalSubjectId(Long.valueOf(body.get("localSubjectId").toString()));
        repo.save(s);
        return ApiResponse.ok(Map.of("message", "Linked successfully"));
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> triggerImport(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importFile(file));
    }
}
