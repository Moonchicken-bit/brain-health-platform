package com.brainhealth.common.exception;

/**
 * Error codes for the entire platform.
 * Range allocation:
 * - 1xxxx: General / System errors
 * - 2xxxx: Auth / Permission errors
 * - 3xxxx: Subject / Session errors
 * - 4xxxx: Scale / Assessment errors
 * - 5xxxx: Imaging errors
 * - 6xxxx: Genetics errors
 * - 7xxxx: Lab errors
 * - 8xxxx: Search / Export errors
 */
public enum ErrorCode {

    // ---- General (10000-10099) ----
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    VALIDATION_FAILED(10001, "数据校验失败"),
    DUPLICATE_ENTRY(10002, "数据重复"),
    FILE_UPLOAD_FAILED(10003, "文件上传失败"),
    FILE_FORMAT_ERROR(10004, "文件格式不支持"),
    FILE_TOO_LARGE(10005, "文件大小超出限制"),

    // ---- Auth / Permission (20000-20099) ----
    UNAUTHORIZED(20001, "未登录或登录已过期"),
    FORBIDDEN(20003, "无权限访问"),
    TOKEN_EXPIRED(20004, "Token已过期"),
    TOKEN_INVALID(20005, "Token无效"),
    USERNAME_OR_PASSWORD_ERROR(20006, "用户名或密码错误"),
    USER_LOCKED(20007, "账号已被锁定"),
    USER_DISABLED(20008, "账号已被禁用"),
    PASSWORD_EXPIRED(20009, "密码已过期，请修改密码"),
    ROLE_NOT_FOUND(20010, "角色不存在"),
    PERMISSION_DENIED(20011, "权限不足"),
    DUPLICATE_USERNAME(20012, "用户名已存在"),

    // ---- Subject / Session (30000-30099) ----
    SUBJECT_NOT_FOUND(30001, "受试者不存在"),
    SUBJECT_ID_DUPLICATE(30002, "受试者ID已存在"),
    SESSION_NOT_FOUND(30003, "访视记录不存在"),
    INSTITUTION_NOT_FOUND(30004, "机构不存在"),
    PROJECT_NOT_FOUND(30005, "项目不存在"),
    COHORT_NOT_FOUND(30006, "队列不存在"),
    IMPORT_TEMPLATE_ERROR(30007, "导入模板格式错误"),
    PATIENT_NOT_FOUND(30008, "患者不存在"),

    // ---- Scale (40000-40099) ----
    SCALE_NOT_FOUND(40001, "量表不存在"),
    ASSESSMENT_NOT_FOUND(40002, "评估记录不存在"),
    ASSESSMENT_LOCKED(40003, "评估记录已锁定，无法修改"),
    SCORE_OUT_OF_RANGE(40004, "评分超出合理范围"),
    EXAMINER_NOT_FOUND(40005, "评估者不存在"),

    // ---- Imaging (50000-50099) ----
    IMAGING_SESSION_NOT_FOUND(50001, "影像检查不存在"),
    SERIES_NOT_FOUND(50002, "影像序列不存在"),
    DICOM_PARSE_ERROR(50003, "DICOM文件解析失败"),
    NIFTI_CONVERSION_ERROR(50004, "NIfTI转换失败"),
    BIDS_EXPORT_ERROR(50005, "BIDS格式导出失败"),
    QC_ALREADY_COMPLETED(50006, "质控已完成，无法重复提交"),
    SCANNER_NOT_FOUND(50007, "扫描设备不存在"),

    // ---- Genetics (60000-60099) ----
    GENETIC_SAMPLE_NOT_FOUND(60001, "遗传样本不存在"),
    VCF_PARSE_ERROR(60002, "VCF文件解析失败"),
    VARIANT_NOT_FOUND(60003, "变异位点不存在"),

    // ---- Lab (70000-70099) ----
    LAB_PANEL_NOT_FOUND(70001, "检验项目不存在"),
    LAB_RESULT_NOT_FOUND(70002, "检验结果不存在"),
    LAB_ORDER_NOT_FOUND(70003, "检验申请不存在"),
    PDF_OCR_ERROR(70004, "PDF报告识别失败"),

    // ---- Search / Export (80000-80099) ----
    SEARCH_ERROR(80001, "检索服务异常"),
    EXPORT_REQUEST_NOT_FOUND(80002, "导出申请不存在"),
    EXPORT_IN_PROGRESS(80003, "导出任务处理中"),
    EXPORT_FAILED(80004, "导出失败"),
    DOWNLOAD_NOT_APPROVED(80005, "下载未获批准"),
    ADNI_IMPORT_ERROR(80006, "ADNI数据导入失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
