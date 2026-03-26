package org.jboss.sbomer.dela.generator.core.port.spi;

/**
 * Outbound port (SPI) for persisting generated Software Bill of Materials (SBOMs).
 * <p>
 * This interface allows the core domain to offload the final generated SBOM document
 * to an external storage system (e.g., a Manifest Storage REST API) and retrieve
 * the precise location where it was stored.
 */
public interface StorageService {

    /**
     * Uploads the finalized CycloneDX SBOM JSON to the external storage service.
     *
     * @param generationId The unique identifier of the generation request this SBOM belongs to.
     * @param sbomJson     The fully serialized CycloneDX SBOM document in JSON format.
     * @return The absolute URL or persistent path where the uploaded SBOM can be accessed.
     */
    String uploadSbom(String generationId, String sbomJson);

}