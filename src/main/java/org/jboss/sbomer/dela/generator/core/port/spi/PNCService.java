package org.jboss.sbomer.dela.generator.core.port.spi;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;

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

    /**
     * Retrieves the build-time dependencies associated with a specific PNC Build ID.
     * This is required by the generation process to manually inject missing NPM
     * dependencies that are not natively captured by the deliverable analyzer.
     *
     * @param buildId The unique identifier of the PNC build.
     * @return A list of Artifacts that were used as dependencies during the build.
     */
    List<Artifact> getNPMDependencies(String buildId);

}
