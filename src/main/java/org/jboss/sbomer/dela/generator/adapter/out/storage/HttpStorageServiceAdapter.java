package org.jboss.sbomer.dela.generator.adapter.out.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
import org.jboss.sbomer.dela.generator.adapter.out.storage.exception.SBOMUploadException;
import org.jboss.sbomer.dela.generator.core.port.spi.StorageService;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class HttpStorageServiceAdapter implements StorageService {

    private final StorageApiClient storageClient;

    public HttpStorageServiceAdapter(@RestClient StorageApiClient storageClient) {
        this.storageClient = storageClient;
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public String uploadSbom(@SpanAttribute("sbom.generationId") String generationId, String sbomJson) {
        log.debug("Uploading SBOM for generation: {}", generationId);
        Path tempFile = null;

        try {
            // Create a unique temporary file (e.g., bom-847329183.json)
            tempFile = Files.createTempFile("bom-", ".json");
            Files.writeString(tempFile, sbomJson);

            // Build the multipart form dynamically
            MultipartFormDataOutput form = new MultipartFormDataOutput();
            form.addFormData(
                    "files",
                    tempFile.toFile(),
                    MediaType.APPLICATION_JSON_TYPE,
                    tempFile.getFileName().toString() // e.g., bom-847329183.json
            );

            // Upload using the REST client
            Map<String, String> response = storageClient.uploadFile(generationId, form);

            // Extract the URL safely
            String uploadedUrl = response.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith(".json"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new SBOMUploadException(null, "Storage service did not return a valid URL for the uploaded JSON"));

            log.debug("Successfully uploaded SBOM to: {}", uploadedUrl);
            return uploadedUrl;

        } catch (Exception e) {
            Span span = Span.current();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());

            String errorCode = null;
            if (e instanceof WebApplicationException wae) {
                errorCode = String.valueOf(wae.getResponse().getStatus());
                log.error("Storage Service rejected upload request. Status: {}, Generation: {}", errorCode, generationId);
            }

            if (e instanceof SBOMUploadException) {
                throw (SBOMUploadException) e;
            }
            throw new SBOMUploadException(errorCode, "Error uploading SBOM: " + e.getMessage(), e);

        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception cleanupEx) {
                log.warn("Failed to clean up temp file after upload attempt: {}", tempFile, cleanupEx);
            }
        }
    }
}
