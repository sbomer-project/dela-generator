package org.jboss.sbomer.dela.generator.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevision;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.enums.BuildType;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NpmDependencyWorkaroundServiceTest {

    private PNCService pncService;
    private NpmDependencyWorkaroundService workaroundService;

    @BeforeEach
    void setUp() {
        pncService = Mockito.mock(PNCService.class);
        workaroundService = new NpmDependencyWorkaroundService(pncService);
    }

    @Test
    void testApplyWorkaroundInjectsHiddenNpmDependencies() {
        // 1. Setup a Maven Build
        Build mavenBuild = Build.builder()
                .id("BUILD-123")
                .buildConfigRevision(BuildConfigurationRevision.builder().buildType(BuildType.MVN).build())
                .build();

        Artifact mavenArtifact = Artifact.builder().purl("pkg:maven/acme").build(mavenBuild).build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(mavenArtifact).build();

        // 2. Setup the existing BOM
        Bom bom = new Bom();
        Component mavenComponent = new Component();
        mavenComponent.setPurl("pkg:maven/acme");
        mavenComponent.setBomRef("pkg:maven/acme");
        bom.addComponent(mavenComponent);
        bom.addDependency(new Dependency("pkg:maven/acme"));

        // 3. Mock the PNC Service to return a hidden NPM dependency for this build
        Artifact hiddenNpmDep = Artifact.builder()
                .id("NPM-999")
                .purl("pkg:npm/lodash@4.17.21")
                .filename("lodash.tgz")
                .identifier("lodash:4.17.21")
                .build();
        when(pncService.getNPMDependencies("BUILD-123")).thenReturn(List.of(hiddenNpmDep));

        // 4. Execute Workaround
        workaroundService.applyWorkaround(bom, List.of(analyzed), "api.pnc.com");

        // 5. Verify
        assertEquals(2, bom.getComponents().size(), "Should have added the NPM component");
        assertTrue(bom.getComponents().stream().anyMatch(c -> c.getPurl().equals("pkg:npm/lodash@4.17.21")));

        // Verify the dependency tree was updated (maven component should now depend on npm component)
        Dependency updatedMavenDep = bom.getDependencies().stream()
                .filter(d -> d.getRef().equals("pkg:maven/acme"))
                .findFirst().orElseThrow();
        
        assertEquals(1, updatedMavenDep.getDependencies().size());
        assertEquals("pkg:npm/lodash@4.17.21", updatedMavenDep.getDependencies().get(0).getRef());
    }
}