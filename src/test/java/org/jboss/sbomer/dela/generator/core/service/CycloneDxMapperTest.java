package org.jboss.sbomer.dela.generator.core.service;

import static org.junit.jupiter.api.Assertions.*;

import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevision;
import org.jboss.pnc.dto.Environment;
import org.jboss.pnc.dto.SCMRepository;
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

    @Test
    void testMapHashes() {
        Artifact artifact = Artifact.builder()
                .id("102")
                .filename("secure-lib.jar")
                .md5("md5-hash-123")
                .sha1("sha1-hash-456")
                .sha256("sha256-hash-789")
                .build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).build();

        Component component = CycloneDxMapper.mapArtifactToComponent(analyzed, "api.pnc.com", "pkg:generic/secure-lib.jar");

        assertNotNull(component.getHashes());
        assertEquals(3, component.getHashes().size());
        assertTrue(component.getHashes().stream()
                .anyMatch(h -> h.getAlgorithm().equals(Hash.Algorithm.MD5.getSpec()) && h.getValue().equals("md5-hash-123")));
        assertTrue(component.getHashes().stream()
                .anyMatch(h -> h.getAlgorithm().equals(Hash.Algorithm.SHA_256.getSpec()) && h.getValue().equals("sha256-hash-789")));
    }

    @Test
    void testMapPedigreeAndTraceability() {
        // 1. Setup rich PNC Build metadata
        Environment env = Environment.builder()
                .systemImageRepositoryUrl("quay.io/rh-newcastle/builder")
                .systemImageId("1.0.0")
                .build();

        SCMRepository scm = SCMRepository.builder()
                .externalUrl("https://github.com/acme/project.git")
                .build();

        BuildConfigurationRevision bcr = BuildConfigurationRevision.builder()
                .scmRevision("external-commit-hash")
                .build();

        Build build = Build.builder()
                .id("BUILD-777")
                .environment(env)
                .scmRepository(scm)
                .scmUrl("https://gitlab.internal/acme/project.git")
                .scmRevision("internal-commit-hash")
                .scmTag("v1.0")
                .scmBuildConfigRevision("external-commit-hash")
                .buildConfigRevision(bcr)
                .build();

        Artifact artifact = Artifact.builder()
                .id("103")
                .filename("built-lib.jar")
                .build(build)
                .build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).build();

        // 2. Execute Mapper
        Component component = CycloneDxMapper.mapArtifactToComponent(analyzed, "https://api.pnc.com", "pkg:generic/built-lib.jar");

        // 3. Verify Traceability (External References)
        assertNotNull(component.getExternalReferences());

        // Check Build ID
        assertTrue(component.getExternalReferences().stream()
                .anyMatch(r -> r.getType() == ExternalReference.Type.BUILD_SYSTEM
                        && "pnc-build-id".equals(r.getComment())
                        && r.getUrl().endsWith("BUILD-777")));

        // Check Environment Meta
        assertTrue(component.getExternalReferences().stream()
                .anyMatch(r -> r.getType() == ExternalReference.Type.BUILD_META
                        && "pnc-environment-image".equals(r.getComment())
                        && r.getUrl().equals("quay.io/rh-newcastle/builder/1.0.0")));

        // 4. Verify Pedigree (Ancestors)
        assertNotNull(component.getPedigree(), "Pedigree should be populated");
        assertNotNull(component.getPedigree().getAncestors(), "Ancestors wrapper should be populated");
        assertNotNull(component.getPedigree().getAncestors().getComponents(), "Ancestors list should be populated");
        assertEquals(2, component.getPedigree().getAncestors().getComponents().size(), "Should have an internal and external ancestor");

        // Verify the internal SCM mapped correctly (Version should be the commit hash, Purl should contain vcs_url)
        Component internalAncestor = component.getPedigree().getAncestors().getComponents().get(0);
        assertEquals("internal-commit-hash", internalAncestor.getVersion());
        assertTrue(internalAncestor.getPurl().contains("vcs_url"), "PURL should contain the VcsUrl qualifier");

        // Verify the external SCM mapped correctly
        Component externalAncestor = component.getPedigree().getAncestors().getComponents().get(1);
        assertEquals("external-commit-hash", externalAncestor.getVersion());
    }

}
