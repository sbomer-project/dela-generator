package org.jboss.sbomer.dela.generator.core.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.stream.Collectors;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.enums.BuildType;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class NpmDependencyWorkaroundService {

    private final PNCService pncService;

    /**
     * Finds components built in PNC (non-NPM builds) and attaches their build-time
     * NPM dependencies into the BOM if they aren't already present.
     */
    public void applyWorkaround(Bom bom, List<AnalyzedArtifact> originalArtifacts, String pncApiUrl) {
        if (bom.getComponents() == null) return;

        // Identify all PNC builds that produced the artifacts in this BOM
        Map<String, List<Component>> buildIdToDependentComponents = new HashMap<>();

        for (AnalyzedArtifact analyzed : originalArtifacts) {
            Build build = analyzed.getArtifact().getBuild();
            if (build != null && build.getBuildConfigRevision() != null 
                && build.getBuildConfigRevision().getBuildType() != BuildType.NPM) {
                
                String buildId = build.getId();
                // Find the matching CycloneDX component we already created
                bom.getComponents().stream()
                        .filter(c -> c.getPurl().equals(analyzed.getArtifact().getPurl()))
                        .findFirst()
                        .ifPresent(c -> buildIdToDependentComponents.computeIfAbsent(buildId, k -> new ArrayList<>()).add(c));
            }
        }

        if (buildIdToDependentComponents.isEmpty()) return;

        // Fetch missing NPM dependencies for those builds
        Set<String> existingPurls = bom.getComponents().stream()
                .map(Component::getPurl)
                .collect(Collectors.toSet());

        Map<String, Component> newNpmComponents = new HashMap<>();
        Map<String, Dependency> bomDependencyMap = initDependencyMap(bom);

        for (Map.Entry<String, List<Component>> entry : buildIdToDependentComponents.entrySet()) {
            String buildId = entry.getKey();
            
            List<Artifact> npmDeps = pncService.getNPMDependencies(buildId);

            if (npmDeps == null || npmDeps.isEmpty()) continue;

            for (Artifact npmArtifact : npmDeps) {
                // If it's already in the BOM, skip it
                if (existingPurls.contains(npmArtifact.getPurl())) continue;

                // Create the new Component
                Component npmComponent = newNpmComponents.computeIfAbsent(npmArtifact.getPurl(), purl -> {
                    Component c = new Component();
                    c.setPurl(purl);
                    c.setBomRef(purl);
                    c.setType(Component.Type.LIBRARY);
                    c.setScope(Component.Scope.REQUIRED);
                    c.setName(npmArtifact.getFilename());
                    c.setVersion(npmArtifact.getIdentifier()); 

                    // Add trace info back to PNC
                    List<org.cyclonedx.model.ExternalReference> refs = new ArrayList<>();
                    org.cyclonedx.model.ExternalReference ref = new org.cyclonedx.model.ExternalReference();
                    ref.setType(org.cyclonedx.model.ExternalReference.Type.BUILD_SYSTEM);
                    ref.setUrl(pncApiUrl + "/pnc-rest/v2/artifacts/" + npmArtifact.getId());
                    ref.setComment("pnc-artifact-id");
                    refs.add(ref);
                    c.setExternalReferences(refs);
                    
                    bom.addComponent(c);
                    log.debug("Added missing NPM dependency: {}", purl);
                    return c;
                });

                // Link the new NPM component as a dependency to the artifacts produced by that build
                Dependency npmDependencyNode = bomDependencyMap.computeIfAbsent(npmComponent.getBomRef(), Dependency::new);
                for (Component producedComponent : entry.getValue()) {
                    Dependency parentNode = bomDependencyMap.computeIfAbsent(producedComponent.getBomRef(), Dependency::new);
                    parentNode.addDependency(npmDependencyNode);
                }
            }
        }

        // Update the BOM with the modified dependency map
        bom.setDependencies(new ArrayList<>(bomDependencyMap.values()));
    }

    private Map<String, Dependency> initDependencyMap(Bom bom) {
        Map<String, Dependency> map = new LinkedHashMap<>();
        if (bom.getDependencies() != null) {
            bom.getDependencies().forEach(d -> map.put(d.getRef(), d));
        }
        return map;
    }
}
