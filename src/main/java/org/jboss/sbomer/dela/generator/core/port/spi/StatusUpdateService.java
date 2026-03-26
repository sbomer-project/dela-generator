package org.jboss.sbomer.dela.generator.core.port.spi;

import java.util.List;

/**
 * Outbound port (SPI) for broadcasting the lifecycle status of a generation task.
 * <p>
 * The core domain calls these explicit methods, and the underlying adapter handles
 * the translation into the standard Avro GenerationUpdate record (including setting
 * the correct status strings, result codes, and populating the baseSbomUrls array).
 */
public interface StatusUpdateService {

    /**
     * Notifies the orchestrator that the generator has actively begun processing the task.
     * Maps to status: 'GENERATING'.
     *
     * @param generationId The unique ID of the generation task.
     */
    void reportGenerating(String generationId);

    /**
     * Notifies the orchestrator that the generation was successful.
     * Maps to status: 'FINISHED', resultCode: 0.
     *
     * @param generationId The unique ID of the generation task.
     * @param baseSbomUrls A list containing the URL(s) of the successfully uploaded SBOM(s).
     */
    void reportFinished(String generationId, List<String> baseSbomUrls);

    /**
     * Notifies the orchestrator that the generation failed.
     * Maps to status: 'FAILED', resultCode: 1 (or other non-zero).
     *
     * @param generationId The unique ID of the generation task.
     * @param reason       A human-readable explanation of the failure.
     */
    void reportFailed(String generationId, String reason);
}