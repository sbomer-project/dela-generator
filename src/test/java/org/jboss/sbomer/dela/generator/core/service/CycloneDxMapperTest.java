package org.jboss.sbomer.dela.generator.core.service;

import static org.junit.jupiter.api.Assertions.*;

import org.cyclonedx.model.Component;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.TargetRepository;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.enums.RepositoryType;
import org.junit.jupiter.api.Test;

class CycloneDxMapperTest {

    @Test
    void testMapMavenArtifact() {
        TargetRepository mavenRepo = TargetRepository.refBuilder().repositoryType(RepositoryType.MAVEN).build();
        Artifact artifact = Artifact.builder()
                .id("100")
                .identifier("org.acme:acme-core:jar:1.0.0")
                .filename("acme-core-1.0.0.jar")
                .targetRepository(mavenRepo)
                .build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).build();

        Component component = CycloneDxMapper.mapArtifactToComponent(analyzed, "api.pnc.com", "pkg:maven/org.acme/acme-core@1.0.0");

        assertEquals("pkg:maven/org.acme/acme-core@1.0.0", component.getPurl());
        assertEquals("org.acme", component.getGroup());
        assertEquals("acme-core", component.getName());
        assertEquals("1.0.0", component.getVersion());
    }

    @Test
    void testMapRedHatArtifactInjectsMetadata() {
        TargetRepository mavenRepo = TargetRepository.refBuilder().repositoryType(RepositoryType.MAVEN).build();
        Artifact artifact = Artifact.builder()
                .id("101")
                .identifier("org.acme:acme-core:jar:1.0.0.redhat-00001")
                .targetRepository(mavenRepo)
                .build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).build();

        Component component = CycloneDxMapper.mapArtifactToComponent(analyzed, "api.pnc.com", "pkg:maven/org.acme/acme-core@1.0.0.redhat-00001");

        assertEquals("Red Hat", component.getPublisher());
        assertNotNull(component.getSupplier());
        assertEquals("Red Hat", component.getSupplier().getName());
        
        // Ensure MRRC distribution URL was added
        assertTrue(component.getExternalReferences().stream()
                .anyMatch(ref -> ref.getUrl().contains("maven.repository.redhat.com")));
    }

    @Test
    void testFallbackToFilenameWhenNoRepository() {
        Artifact artifact = Artifact.builder()
                .id("999")
                .filename("weird-binary.bin")
                .build(); // No TargetRepository
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).build();

        Component component = CycloneDxMapper.mapArtifactToComponent(analyzed, "api.pnc.com", "pkg:generic/weird-binary.bin");

        assertEquals("weird-binary.bin", component.getName());
        assertEquals("unknown", component.getVersion());
    }
}
