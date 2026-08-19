package com.brainhealth.common.constant;

/**
 * Platform-wide constants.
 */
public final class Constants {

    private Constants() {}

    // ---- HTTP Headers ----
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_BEARER_PREFIX = "Bearer ";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    // ---- Redis Key Prefixes ----
    public static final String REDIS_TOKEN_BLACKLIST = "token:blacklist:";
    public static final String REDIS_REFRESH_TOKEN = "token:refresh:";
    public static final String REDIS_USER_PERMISSIONS = "user:permissions:";
    public static final String REDIS_SUBJECT_CACHE = "subject:cache:";
    public static final String REDIS_RATE_LIMIT = "rate:limit:";

    // ---- RabbitMQ ----
    public static final String MQ_EXCHANGE_AUDIT = "brainhealth.audit.exchange";
    public static final String MQ_EXCHANGE_SEARCH = "brainhealth.search.exchange";
    public static final String MQ_QUEUE_AUDIT_LOG = "brainhealth.audit.log.queue";
    public static final String MQ_QUEUE_SEARCH_INDEX = "brainhealth.search.index.queue";
    public static final String MQ_ROUTING_AUDIT = "audit.log";
    public static final String MQ_ROUTING_SEARCH = "search.index";

    // ---- File Storage Buckets (MinIO) ----
    public static final String MINIO_BUCKET_DICOM = "dicom";
    public static final String MINIO_BUCKET_NIFTI = "nifti";
    public static final String MINIO_BUCKET_BIDS = "bids";
    public static final String MINIO_BUCKET_VCF = "vcf";
    public static final String MINIO_BUCKET_EEG = "eeg";
    public static final String MINIO_BUCKET_EXPORT = "export";
    public static final String MINIO_BUCKET_CONSENT = "consent";

    // ---- File Size Limits ----
    public static final long MAX_UPLOAD_SIZE = 10L * 1024 * 1024 * 1024; // 10 GB
    public static final long MAX_DICOM_ARCHIVE_SIZE = 5L * 1024 * 1024 * 1024; // 5 GB
    public static final long MAX_VCF_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2 GB
    public static final long CHUNK_SIZE = 5L * 1024 * 1024; // 5 MB per chunk

    // ---- Data Standards ----
    public static final String FHIR_VERSION = "R4";
    public static final String BIDS_VERSION = "1.8.0";
    public static final String HGVS_VERSION = "20.05";
    public static final String REFERENCE_GENOME = "GRCh38";

    // ---- Subject ID Format ----
    public static final String SUBJECT_ID_PREFIX = "SUB-";
    public static final String SESSION_LABEL_PATTERN = "V%02d";

    // ---- Pagination Defaults ----
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
