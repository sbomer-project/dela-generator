package org.jboss.sbomer.dela.generator.core.service;

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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;

import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.sbomer.dela.generator.core.port.api.GenerationProcessor;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;
import org.jboss.sbomer.dela.generator.core.port.spi.StatusUpdateService;
import org.jboss.sbomer.dela.generator.core.port.spi.StorageService;
import org.jboss.sbomer.events.orchestration.GenerationCreated;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class DelaGenerationService implements GenerationProcessor {

    private final PNCService pncService;
    private final StorageService storageService;
    private final StatusUpdateService statusUpdateService;

    @Override
    public void processGeneration(GenerationCreated event) {
        String generationId = event.getData().getGenerationRequest().getGenerationId();
        String operationId = event.getData().getGenerationRequest().getTarget().getIdentifier();

        log.info("Starting batch DELA generation for Operation: {}", operationId);

        try {
            statusUpdateService.reportGenerating(generationId);

            List<AnalyzedArtifact> allArtifacts = pncService.getAnalyzedArtifacts(operationId);

            Map<String, List<AnalyzedArtifact>> groupedByZip = allArtifacts.stream()
                    .filter(a -> a.getDistribution() != null && a.getDistribution().getDistributionUrl() != null)
                    .collect(Collectors.groupingBy(a -> a.getDistribution().getDistributionUrl()));

            if (groupedByZip.isEmpty()) {
                throw new IllegalStateException("No valid deliverable distributions found in operation " + operationId);
            }

            List<String> finalSbomUrls = new ArrayList<>();

            for (Map.Entry<String, List<AnalyzedArtifact>> entry : groupedByZip.entrySet()) {
                String zipUrl = entry.getKey();
                List<AnalyzedArtifact> artifactsInZip = entry.getValue();

                // --- THE CORE MAPPING STEP ---
                String sbomJson = generateCycloneDxJson(zipUrl, artifactsInZip);

                String uploadedUrl = storageService.uploadSbom(generationId, sbomJson);
                finalSbomUrls.add(uploadedUrl);
            }

            statusUpdateService.reportFinished(generationId, finalSbomUrls);

        } catch (Exception e) {
            log.error("Batch generation failed for Operation: {}", operationId, e);
            statusUpdateService.reportFailed(generationId, "Batch processing failed: " + e.getMessage());
        }
    }

    /**
     * Transforms a list of PNC artifacts into a physical CycloneDX JSON string.
     * This implementation tries to follow the legacy SBOMer logic:
     * - Uses UUID v3 (Name-based) for deterministic Serial Numbers.
     * - Identifies SBOMer name and version as the generating tool in Metadata.
     * - Builds a hierarchical tree based on archive paths.
     * - Sets the root distribution as the primary component.
     */
    private String generateCycloneDxJson(String zipUrl, List<AnalyzedArtifact> artifacts) throws GeneratorException {
        Bom bom = new Bom();

        // 1. Create the Root Component (The Deliverable Distribution)
        String fileName = extractFilename(zipUrl);
        String rootPurl = "pkg:generic/" + fileName;

        Component rootComponent = new Component();
        rootComponent.setName(fileName);
        rootComponent.setPurl(rootPurl);
        rootComponent.setBomRef(rootPurl);
        rootComponent.setType(Component.Type.FILE);

        // 2. Metadata: Identify SBOMer as the Tool
        Metadata metadata = new Metadata();
        metadata.setTimestamp(new java.util.Date());
        metadata.setComponent(rootComponent);

        // Set Tool Information (Loyal to legacy tool identification)
        org.cyclonedx.model.metadata.ToolInformation toolInfo = new org.cyclonedx.model.metadata.ToolInformation();
        org.cyclonedx.model.Service toolService = new org.cyclonedx.model.Service();
        toolService.setName("SBOMer");
        toolService.setVersion("2.0.0"); // Update based on your actual project version
        toolInfo.setServices(List.of(toolService));
        metadata.setToolChoice(toolInfo);

        // Set Supplier (Red Hat)
        org.cyclonedx.model.OrganizationalEntity supplier = new org.cyclonedx.model.OrganizationalEntity();
        supplier.setName("Red Hat");
        supplier.setUrls(List.of("https://www.redhat.com"));
        metadata.setSupplier(supplier);

        bom.setMetadata(metadata);

        // 3. Setup Tracking Maps for Hierarchy
        Dependency rootDependency = new Dependency(rootPurl);
        bom.addDependency(rootDependency);

        Map<String, Component> purlToComponents = new HashMap<>();
        Map<String, Dependency> purlToDependencies = new HashMap<>();
        Map<String, Dependency> pathToDependencies = new TreeMap<>();

        purlToComponents.put(rootPurl, rootComponent);
        purlToDependencies.put(rootPurl, rootDependency);

        // 4. Map Artifacts to Components
        for (AnalyzedArtifact artifact : artifacts) {
            String purl = artifact.getArtifact().getPurl();

            if (!purlToComponents.containsKey(purl)) {
                // mapArtifactToComponent handles the GAV/NPM coords and Licenses
                Component component = CycloneDxMapper.mapArtifactToComponent(artifact);
                bom.addComponent(component);
                purlToComponents.put(purl, component);

                Dependency dependency = new Dependency(purl);
                bom.addDependency(dependency);
                purlToDependencies.put(purl, dependency);
            }

            // Map internal paths for tree reconstruction
            Dependency depNode = purlToDependencies.get(purl);
            if (artifact.getArchiveFilenames() != null) {
                artifact.getArchiveFilenames().forEach(path -> pathToDependencies.put(path, depNode));
            }
        }

        // 5. Build Hierarchy (Nesting JARs/WARs)
        for (Map.Entry<String, Dependency> entry : pathToDependencies.entrySet()) {
            String path = entry.getKey();
            Dependency current = entry.getValue();

            Optional<Dependency> parentNode = findClosestParent(pathToDependencies, path);
            parentNode.orElse(rootDependency).addDependency(current);
        }

        // 6. Final Structural Cleanup
        if (bom.getComponents() == null) bom.setComponents(new ArrayList<>());
        if (bom.getComponents().isEmpty() || !rootComponent.getBomRef().equals(bom.getComponents().get(0).getBomRef())) {
            bom.getComponents().add(0, rootComponent);
        }

        // Prevent self-referencing dependencies
        if (bom.getDependencies() != null) {
            bom.getDependencies().forEach(d -> {
                if (d.getDependencies() != null) {
                    d.getDependencies().removeIf(child -> child.getRef().equals(d.getRef()));
                }
            });
        }

        // 7. Deterministic Serial Number (UUID v3)
        // First, generate the JSON without the serial number
        BomJsonGenerator generator = new BomJsonGenerator(bom, Version.VERSION_16);
        String rawJson = generator.toJsonString();

        // Create a deterministic UUID based on the BOM's content
        byte[] bytes = rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String deterministicUuid = UUID.nameUUIDFromBytes(bytes).toString();
        bom.setSerialNumber("urn:uuid:" + deterministicUuid);

        // Return the final JSON with the serial number now included
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
}