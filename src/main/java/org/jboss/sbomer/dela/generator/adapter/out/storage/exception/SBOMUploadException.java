package org.jboss.sbomer.dela.generator.adapter.out.storage.exception;

public class SBOMUploadException extends RuntimeException {
    
    private final String errorCode;

    public SBOMUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SBOMUploadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}