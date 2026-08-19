package com.brainhealth.genetics.repository;
import com.brainhealth.genetics.entity.GeneticsVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GeneticsVariantRepository extends JpaRepository<GeneticsVariant, Long>, JpaSpecificationExecutor<GeneticsVariant> {
    Page<GeneticsVariant> findBySampleId(Long sampleId, Pageable pageable);
    Page<GeneticsVariant> findBySampleIdAndGeneSymbolContaining(Long sampleId, String geneSymbol, Pageable pageable);
    void deleteBySampleId(Long sampleId);
}
