package com.brainhealth.subject.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a batch import operation.
 * Reports success/failure counts and per-row error messages.
 */
public class ImportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileName;
    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<String> errors;

    public ImportResult() {
        this.errors = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
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

    /**
     * Add a row-level error message.
     */
    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.errorCount = this.errors.size();
    }
}
