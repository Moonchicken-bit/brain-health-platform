package com.brainhealth.genetics.entity;
import com.brainhealth.common.model.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "genetics_variant")
public class GeneticsVariant extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sample_id", nullable = false)
    private Long sampleId;
    @Column(name = "gene_symbol", length = 100)
    private String geneSymbol;
    @Column(name = "variant_type", length = 30)
    private String variantType;
    @Column(name = "clinical_significance", length = 50)
    private String clinicalSignificance;
    @Column(name = "chromosome", length = 10)
    private String chromosome;
    @Column(name = "position")
    private Long position;
    @Column(name = "ref", length = 500)
    private String ref;
    @Column(name = "alt", length = 500)
    private String alt;
    @Column(name = "rs_id", length = 50)
    private String rsId;
    @Column(name = "impact", length = 30)
    private String impact;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "allele_frequency")
    private Double alleleFrequency;
    @Column(name = "read_depth")
    private Integer readDepth;
    @Column(name = "genotype", length = 10)
    private String genotype;
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long v) { sampleId = v; }
    public String getGeneSymbol() { return geneSymbol; }
    public void setGeneSymbol(String v) { geneSymbol = v; }
    public String getVariantType() { return variantType; }
    public void setVariantType(String v) { variantType = v; }
    public String getClinicalSignificance() { return clinicalSignificance; }
    public void setClinicalSignificance(String v) { clinicalSignificance = v; }
    public String getChromosome() { return chromosome; }
    public void setChromosome(String v) { chromosome = v; }
    public Long getPosition() { return position; }
    public void setPosition(Long v) { position = v; }
    public String getRef() { return ref; }
    public void setRef(String v) { ref = v; }
    public String getAlt() { return alt; }
    public void setAlt(String v) { alt = v; }
    public String getRsId() { return rsId; }
    public void setRsId(String v) { rsId = v; }
    public String getImpact() { return impact; }
    public void setImpact(String v) { impact = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Double getAlleleFrequency() { return alleleFrequency; }
    public void setAlleleFrequency(Double v) { alleleFrequency = v; }
    public Integer getReadDepth() { return readDepth; }
    public void setReadDepth(Integer v) { readDepth = v; }
    public String getGenotype() { return genotype; }
    public void setGenotype(String v) { genotype = v; }
}
