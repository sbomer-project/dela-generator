package org.jboss.sbomer.dela.generator.core.port.api;

import org.jboss.sbomer.events.orchestration.GenerationCreated;

/**
 * Inbound port (Driving API) for the Deliverables Analyzer generator domain.
 * <p>
 * This interface defines the primary entry point into the core business logic.
 * Adapters (e.g., a Kafka consumer) will call this interface to initiate the
 * SBOM generation process without the core domain needing to know how the
 * trigger was received.
 */
public interface GenerationProcessor {

    /**
     * Orchestrates the fetching, mapping, and uploading of a CycloneDX SBOM
     * based on a newly created generation request.
     *
     * @param generationCreated The orchestration event containing the operation ID
     * and configuration needed to generate the SBOM.
     */
    void processGeneration(GenerationCreated generationCreated);

}
