package com.brainhealth.genetics.service;

import com.brainhealth.common.model.PageResult;
import com.brainhealth.genetics.entity.GeneticsSample;
import com.brainhealth.genetics.entity.GeneticsVariant;
import com.brainhealth.genetics.repository.GeneticsSampleRepository;
import com.brainhealth.genetics.repository.GeneticsVariantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class GeneticsService {
    private static final int SAVE_BATCH_SIZE = 1_000;
    private final GeneticsSampleRepository sampleRepo;
    private final GeneticsVariantRepository variantRepo;
    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    public GeneticsService(GeneticsSampleRepository sampleRepo,
                           GeneticsVariantRepository variantRepo,
                           JdbcTemplate jdbc,
                           @Value("${genetics.upload.storage-root:./data/genetics}") String storageRoot) {
        this.sampleRepo = sampleRepo;
        this.variantRepo = variantRepo;
        this.jdbc = jdbc;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public PageResult<GeneticsSample> listSamples(Long subjectId, String sampleType, String platform,
                                                  String qcStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<GeneticsSample> result;
        if (subjectId != null) result = sampleRepo.findBySubjectId(subjectId, pageable);
        else if (sampleType != null) result = sampleRepo.findBySampleType(sampleType, pageable);
        else if (platform != null) result = sampleRepo.findByPlatform(platform, pageable);
        else if (qcStatus != null) result = sampleRepo.findByQcStatus(qcStatus, pageable);
        else result = sampleRepo.findAll(pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public PageResult<GeneticsSample> listSamplesForSubjects(Collection<Long> subjectIds, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<GeneticsSample> result = subjectIds.isEmpty()
            ? Page.empty(pageable) : sampleRepo.findBySubjectIdIn(subjectIds, pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public GeneticsSample getSample(Long id) {
        return sampleRepo.findById(id).orElse(null);
    }

    @Transactional
    public GeneticsSample createSample(GeneticsSample sample) {
        return sampleRepo.save(sample);
    }

    @Transactional
    public void deleteSample(Long id) {
        variantRepo.deleteBySampleId(id);
        sampleRepo.deleteById(id);
    }

    public PageResult<GeneticsVariant> listVariants(Long sampleId, String geneSymbol, String variantType,
                                                    String clinicalSignificance, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("position").ascending());
        Specification<GeneticsVariant> spec = (root, query, cb) -> cb.equal(root.get("sampleId"), sampleId);
        if (hasText(geneSymbol)) {
            String value = "%" + geneSymbol.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("geneSymbol")), value));
        }
        if (hasText(variantType)) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.upper(root.get("variantType")), variantType.trim().toUpperCase(Locale.ROOT)));
        }
        if (hasText(clinicalSignificance)) {
            spec = spec.and((root, query, cb) -> cb.equal(
                    cb.upper(root.get("clinicalSignificance")),
                    clinicalSignificance.trim().toUpperCase(Locale.ROOT)));
        }
        Page<GeneticsVariant> result = variantRepo.findAll(spec, pageable);
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    public GeneticsVariant getVariant(Long sampleId, Long variantId) {
        return variantRepo.findById(variantId)
                .filter(variant -> sampleId.equals(variant.getSampleId()))
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> parseVcf(Long sampleId) {
        GeneticsSample sample = sampleRepo.findById(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("遗传样本不存在"));
        Path source = resolveStoredFile(sample.getVcfFilePath());
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("VCF 文件不存在或已被移动");
        }

        variantRepo.deleteBySampleId(sampleId);
        List<GeneticsVariant> batch = new ArrayList<>(SAVE_BATCH_SIZE);
        long variantCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                openPossiblyGzipped(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) == '#') continue;
                GeneticsVariant variant = parseVariantLine(sampleId, line);
                if (variant == null) continue;
                batch.add(variant);
                variantCount++;
                if (batch.size() == SAVE_BATCH_SIZE) {
                    variantRepo.saveAll(batch);
                    variantRepo.flush();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) variantRepo.saveAll(batch);
        } catch (IOException e) {
            throw new IllegalStateException("读取 VCF 文件失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            sample.setQcStatus("FAILED");
            sampleRepo.save(sample);
            throw e;
        }

        sample.setVariantCount(Math.toIntExact(Math.min(variantCount, Integer.MAX_VALUE)));
        sample.setQcStatus("PASSED");
        sampleRepo.save(sample);
        return Map.of("status", "completed", "variantsFound", variantCount,
                "message", "已从 VCF 解析 " + variantCount + " 条变异");
    }

    private GeneticsVariant parseVariantLine(Long sampleId, String line) {
        String[] columns = line.split("\t", -1);
        if (columns.length < 8) {
            throw new IllegalArgumentException("VCF 数据行不足 8 列");
        }
        long position;
        try {
            position = Long.parseLong(columns[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("VCF POS 不是有效整数：" + columns[1]);
        }
        String[] alternateAlleles = columns[4].split(",");
        Map<String, String> info = parseInfo(columns[7]);
        String genotype = columns.length > 9 ? parseGenotype(columns[8], columns[9]) : null;
        String alt = alternateAlleles[0];

        GeneticsVariant variant = new GeneticsVariant();
        variant.setSampleId(sampleId);
        variant.setChromosome(columns[0]);
        variant.setPosition(position);
        variant.setRsId(".".equals(columns[2]) ? null : columns[2]);
        variant.setRef(columns[3]);
        variant.setAlt(columns[4]);
        variant.setVariantType(variantType(columns[3], alt, info));
        variant.setGeneSymbol(firstInfo(info, "GENE", "SYMBOL", "Gene.refGene"));
        variant.setClinicalSignificance(firstInfo(info, "CLNSIG", "CLIN_SIG"));
        variant.setImpact(firstInfo(info, "IMPACT"));
        variant.setDescription(firstInfo(info, "CLNDN", "ANN", "CSQ"));
        variant.setAlleleFrequency(parseDouble(firstInfo(info, "AF", "gnomAD_AF")));
        variant.setReadDepth(parseInteger(firstInfo(info, "DP")));
        variant.setGenotype(genotype);
        return variant;
    }

    private Path resolveStoredFile(String storedPath) {
        if (!hasText(storedPath)) throw new IllegalStateException("样本未关联 VCF 文件");
        Path path = Path.of(storedPath);
        Path resolved = path.isAbsolute() ? path.normalize() : storageRoot.resolve(path).normalize();
        if (!resolved.startsWith(storageRoot)) throw new SecurityException("VCF 文件路径越界");
        return resolved;
    }

    private InputStream openPossiblyGzipped(Path source) throws IOException {
        InputStream stream = Files.newInputStream(source);
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return new GZIPInputStream(stream);
        }
        return stream;
    }

    private static Map<String, String> parseInfo(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.equals(".")) return result;
        for (String part : value.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0) result.put(part.substring(0, separator), part.substring(separator + 1));
            else if (!part.isBlank()) result.put(part, "true");
        }
        return result;
    }

    private static String firstInfo(Map<String, String> info, String... keys) {
        for (String key : keys) {
            String value = info.get(key);
            if (hasText(value) && !".".equals(value)) return value;
        }
        return null;
    }

    private static String variantType(String ref, String alt, Map<String, String> info) {
        String declared = firstInfo(info, "SVTYPE", "TYPE");
        if (declared != null) return declared.toUpperCase(Locale.ROOT);
        if (alt.startsWith("<") || alt.contains("[") || alt.contains("]")) return "SV";
        if (ref.length() == 1 && alt.length() == 1) return "SNP";
        if (ref.length() < alt.length()) return "INS";
        if (ref.length() > alt.length()) return "DEL";
        return "MNV";
    }

    private static String parseGenotype(String format, String sampleValue) {
        String[] keys = format.split(":", -1);
        String[] values = sampleValue.split(":", -1);
        for (int i = 0; i < keys.length && i < values.length; i++) {
            if ("GT".equals(keys[i])) return ".".equals(values[i]) ? null : values[i];
        }
        return null;
    }

    private static Double parseDouble(String value) {
        if (!hasText(value)) return null;
        try {
            return Double.valueOf(value.split(",")[0]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (!hasText(value)) return null;
        try {
            return Integer.valueOf(value.split(",")[0]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public Map<String, Object> getVariantSummary(Long sampleId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT variant_type, COUNT(*) AS cnt FROM genetics_variant WHERE sample_id=? GROUP BY variant_type",
                sampleId);
        Map<String, Object> summary = new LinkedHashMap<>();
        int total = 0;
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("variant_type");
            int count = ((Number) row.get("cnt")).intValue();
            summary.put(type != null ? type.toLowerCase(Locale.ROOT) + "Count" : "otherCount", count);
            total += count;
        }
        summary.put("totalVariants", total);
        return summary;
    }

    public Map<String, Object> getVariantsBySubject(Long subjectId, int page, int size) {
        List<Long> sampleIds = jdbc.queryForList(
                "SELECT id FROM genetics_sample WHERE subject_id=?", Long.class, subjectId);
        if (sampleIds.isEmpty()) return Map.of("records", Collections.emptyList(), "total", 0);
        String sql = "SELECT gv.*, gs.subject_id FROM genetics_variant gv " +
                "JOIN genetics_sample gs ON gv.sample_id=gs.id WHERE gs.subject_id=? LIMIT ? OFFSET ?";
        List<Map<String, Object>> records =
                jdbc.queryForList(sql, subjectId, size, (page - 1) * size);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM genetics_variant gv JOIN genetics_sample gs ON gv.sample_id=gs.id " +
                        "WHERE gs.subject_id=?", Long.class, subjectId);
        return Map.of("records", records, "total", total != null ? total : 0);
    }
}
