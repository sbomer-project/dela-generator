package org.jboss.sbomer.dela.generator.core.port.spi;

import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;

import java.util.List;

/**
 * Outbound port (SPI) for interacting with the Project Newcastle (PNC) system.
 * <p>
 * This interface abstracts away the underlying HTTP/REST communication. The core
 * domain uses this to request deliverable metadata and artifact lists, remaining
 * completely agnostic to the actual network implementation or REST clients used.
 */
public interface PNCService {

    /**
     * Retrieves the metadata for a specific Deliverable Analyzer operation.
     *
     * @param operationId The unique identifier of the PNC operation.
     * @return The operation details, including product milestones and configuration.
     */
    DeliverableAnalyzerOperation getOperation(String operationId);

    /**
     * Retrieves the complete list of artifacts analyzed during the specified operation.
     *
     * @param operationId The unique identifier of the PNC operation.
     * @return A list of artifacts, including their PURLs, distribution URLs, and archive paths.
     */
    List<AnalyzedArtifact> getAnalyzedArtifacts(String operationId);

}