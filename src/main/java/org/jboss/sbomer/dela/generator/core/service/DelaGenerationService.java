package org.jboss.sbomer.dela.generator.core.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.sbomer.dela.generator.core.port.api.GenerationProcessor;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;
import org.jboss.sbomer.dela.generator.core.port.spi.StatusUpdateService;
import org.jboss.sbomer.dela.generator.core.port.spi.StorageService;
import org.jboss.sbomer.events.orchestration.GenerationCreated;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class DelaGenerationService implements GenerationProcessor {

    private final PNCService pncService;
    private final StorageService storageService;
    private final StatusUpdateService statusUpdateService;
    private final NpmDependencyWorkaroundService npmWorkaroundService;

    // --- Injected Configuration Properties ---
    @ConfigProperty(name = "sbomer.generator.tool.name", defaultValue = "SBOMer NextGen")
    String toolName;

    @ConfigProperty(name = "sbomer.generator.tool.version", defaultValue = "1.0.0")
    String toolVersion;

    @ConfigProperty(name = "sbomer.generator.supplier.name", defaultValue = "Red Hat")
    String supplierName;

    @ConfigProperty(name = "sbomer.generator.supplier.urls", defaultValue = "https://www.redhat.com")
    List<String> supplierUrls;

    @ConfigProperty(name = "pnc.api.url", defaultValue = "https://pnc-url")
    String pncApiUrl;

    @Override
    @Bulkhead(value = 5) // MAXIMUM 5 concurrent generation tasks allowed
    public void processGeneration(GenerationCreated event) {
        String generationId = event.getData().getGenerationRequest().getGenerationId();
        String operationId = event.getData().getGenerationRequest().getTarget().getIdentifier();

        log.info("Starting batch DELA generation for Operation: {}", operationId);

        try {
            statusUpdateService.reportGenerating(generationId);

            // Fetch the operation metadata for traceability and version fallback
            DeliverableAnalyzerOperation operation = pncService.getOperation(operationId);
            List<AnalyzedArtifact> allArtifacts = pncService.getAnalyzedArtifacts(operationId);

            Map<String, List<AnalyzedArtifact>> groupedByDeliverable = allArtifacts.stream()
                    .filter(a -> a.getDistribution() != null && a.getDistribution().getDistributionUrl() != null)
                    .collect(Collectors.groupingBy(a -> a.getDistribution().getDistributionUrl()));

            if (groupedByDeliverable.isEmpty()) {
                throw new IllegalStateException("No valid deliverable distributions found in operation " + operationId);
            }

            List<String> finalSbomUrls = new ArrayList<>();

            for (Map.Entry<String, List<AnalyzedArtifact>> entry : groupedByDeliverable.entrySet()) {
                String deliverableUrl = entry.getKey();
                List<AnalyzedArtifact> artifactsInDeliverable = entry.getValue();

                // Pass the operation object down into the generator
                String sbomJson = generateCycloneDxJson(deliverableUrl, artifactsInDeliverable, operation);

                String uploadedUrl = storageService.uploadSbom(generationId, sbomJson);
                finalSbomUrls.add(uploadedUrl);
            }

            statusUpdateService.reportFinished(generationId, finalSbomUrls);

        } catch (Throwable t) {
            log.error("Batch generation failed for Operation: {}", operationId, t);
            statusUpdateService.reportFailed(generationId, "Batch processing failed: " + t.getMessage());
            throw new RuntimeException("Failing Kafka message due to internal processing error", t);
        }
    }

    private String generateCycloneDxJson(String deliverableUrl, List<AnalyzedArtifact> artifacts, DeliverableAnalyzerOperation operation) throws GeneratorException {
        Bom bom = new Bom();

        String fileName = extractFilename(deliverableUrl);
        String rootPurl = "pkg:generic/" + fileName;

        // Setup Root Component
        Component rootComponent = new Component();
        rootComponent.setName(fileName);
        rootComponent.setPurl(rootPurl);
        rootComponent.setBomRef(rootPurl);
        rootComponent.setType(Component.Type.FILE);

        // Extract distribution SHA-256 for root component metadata
        Optional<String> distSha256 = extractDistributionSha256(artifacts);
        if (distSha256.isPresent()) {
            rootComponent.setVersion("sha256:" + distSha256.get());
        } else if (operation != null && operation.getProductMilestone() != null) {
            // Legacy Parity: Fallback to milestone version if no checksum is available
            rootComponent.setVersion(operation.getProductMilestone().getVersion());
        }

        // Add Red Hat Deliverable Properties
        List<Property> rootProperties = new ArrayList<>();
        rootProperties.add(createProperty("redhat:deliverable-url", deliverableUrl));
        distSha256.ifPresent(sha -> rootProperties.add(createProperty("redhat:deliverable-checksum", "sha256:" + sha)));
        rootComponent.setProperties(rootProperties);

        // Add the PNC Operation Traceability
        if (operation != null) {
            org.cyclonedx.model.ExternalReference opRef = new org.cyclonedx.model.ExternalReference();
            opRef.setType(org.cyclonedx.model.ExternalReference.Type.BUILD_SYSTEM);
            opRef.setUrl(pncApiUrl + "/pnc-rest/v2/operations/deliverable-analyzer/" + operation.getId());
            opRef.setComment("pnc-operation-id");

            List<org.cyclonedx.model.ExternalReference> refs = new ArrayList<>();
            refs.add(opRef);
            rootComponent.setExternalReferences(refs);
        }

        // Metadata (Tool & Supplier)
        Metadata metadata = new Metadata();
        metadata.setTimestamp(new java.util.Date());
        metadata.setComponent(rootComponent);

        org.cyclonedx.model.metadata.ToolInformation toolInfo = new org.cyclonedx.model.metadata.ToolInformation();
        org.cyclonedx.model.Service toolService = new org.cyclonedx.model.Service();
        toolService.setName(toolName);
        toolService.setVersion(toolVersion);
        toolInfo.setServices(List.of(toolService));
        metadata.setToolChoice(toolInfo);

        org.cyclonedx.model.OrganizationalEntity supplier = new org.cyclonedx.model.OrganizationalEntity();
        supplier.setName(supplierName);
        supplier.setUrls(supplierUrls);
        metadata.setSupplier(supplier);

        bom.setMetadata(metadata);

        // Dependency Tracking & Component Mapping
        Dependency rootDependency = new Dependency(rootPurl);
        bom.addDependency(rootDependency);

        Map<String, Component> purlToComponents = new HashMap<>();
        Map<String, Dependency> purlToDependencies = new HashMap<>();
        Map<String, Dependency> pathToDependencies = new TreeMap<>();

        purlToComponents.put(rootPurl, rootComponent);
        purlToDependencies.put(rootPurl, rootDependency);

        for (AnalyzedArtifact artifact : artifacts) {
            String purl = artifact.getArtifact().getPurl();

            // Handle missing PURLs gracefully
            if (purl == null || purl.isBlank()) {
                String safeName = artifact.getArtifact().getFilename() != null
                        ? artifact.getArtifact().getFilename()
                        : "unknown-artifact-" + artifact.getArtifact().getId();
                purl = "pkg:generic/" + safeName;
            }

            if (!purlToComponents.containsKey(purl)) {
                // Pass pncApiUrl and the resolved purl into the mapper
                Component component = CycloneDxMapper.mapArtifactToComponent(artifact, pncApiUrl, purl);
                bom.addComponent(component);
                purlToComponents.put(purl, component);

                Dependency dependency = new Dependency(purl);
                bom.addDependency(dependency);
                purlToDependencies.put(purl, dependency);
            }

            Dependency depNode = purlToDependencies.get(purl);
            if (artifact.getArchiveFilenames() != null) {
                artifact.getArchiveFilenames().forEach(path -> pathToDependencies.put(path, depNode));
            }
        }

        // Build Hierarchy Tree
        for (Map.Entry<String, Dependency> entry : pathToDependencies.entrySet()) {
            String path = entry.getKey();
            Dependency current = entry.getValue();

            Optional<Dependency> parentNode = findClosestParent(pathToDependencies, path);
            parentNode.orElse(rootDependency).addDependency(current);
        }

        // Apply NPM Dependencies Workaround
        npmWorkaroundService.applyWorkaround(bom, artifacts, pncApiUrl);

        // Final Structural Cleanup
        if (bom.getComponents() == null) bom.setComponents(new ArrayList<>());
        if (bom.getComponents().isEmpty() || !rootComponent.getBomRef().equals(bom.getComponents().get(0).getBomRef())) {
            bom.getComponents().add(0, rootComponent);
        }

        if (bom.getDependencies() != null) {
            bom.getDependencies().forEach(d -> {
                if (d.getDependencies() != null) {
                    // Prevent infinite loops / self-references
                    d.getDependencies().removeIf(child -> child.getRef().equals(d.getRef()));
                }
            });
        }

        // Deterministic UUID Generation
        BomJsonGenerator generator = new BomJsonGenerator(bom, Version.VERSION_16);
        String rawJson = generator.toJsonString();

        byte[] bytes = rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String deterministicUuid = UUID.nameUUIDFromBytes(bytes).toString();
        bom.setSerialNumber("urn:uuid:" + deterministicUuid);

        return new BomJsonGenerator(bom, Version.VERSION_16).toJsonString();
    }

    private Optional<Dependency> findClosestParent(Map<String, Dependency> pathMap, String path) {
        String currentPath = path;
        while (currentPath.contains("!/")) {
            currentPath = currentPath.substring(0, currentPath.lastIndexOf("!/"));
            if (pathMap.containsKey(currentPath)) {
                return Optional.of(pathMap.get(currentPath));
            }
        }
        return Optional.empty();
    }

    private String extractFilename(String url) {
        try {
            return Paths.get(new URI(url).getPath()).getFileName().toString();
        } catch (Exception e) {
            return "unknown-deliverable";
        }
    }

    private Optional<String> extractDistributionSha256(List<AnalyzedArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty() || artifacts.get(0).getDistribution() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(artifacts.get(0).getDistribution().getSha256());
    }

    private Property createProperty(String name, String value) {
        Property property = new Property();
        property.setName(name);
        property.setValue(value);
        return property;
    }
}