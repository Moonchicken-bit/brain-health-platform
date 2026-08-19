package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BatchImportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private int successCount;
    private int errorCount;
    private List<String> errors;

    public BatchImportResult() {
        this.errors = new ArrayList<>();
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
