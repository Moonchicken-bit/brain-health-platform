CREATE TABLE IF NOT EXISTS genetics_platform (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_genetics_platform_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reference_genome (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reference_genome_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO genetics_platform(code,name,sort_order) VALUES
('ILLUMINA_NOVASEQ','Illumina NovaSeq',10),
('ILLUMINA_HISEQ','Illumina HiSeq',20),
('BGI_DNBSEQ','BGI DNBSEQ',30),
('ONT_PROMETHION','Oxford Nanopore PromethION',40),
('PACBIO_SEQUEL','PacBio Sequel',50);

INSERT IGNORE INTO reference_genome(code,name,sort_order) VALUES
('hg38','GRCh38 / hg38',10),
('hg19','GRCh37 / hg19',20),
('T2T-CHM13','T2T-CHM13',30);
