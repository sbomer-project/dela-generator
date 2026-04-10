package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockPncServiceAdapterTest {

    private MockPncServiceAdapter mockAdapter;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockPncServiceAdapter();
    }

    @Test
    void testGetOperationReturnsMockData() {
        DeliverableAnalyzerOperation op = mockAdapter.getOperation("ANY-ID");

        assertNotNull(op);
        assertEquals("ANY-ID", op.getId());
        assertNotNull(op.getProductMilestone());
        assertEquals("1.0.0.GA", op.getProductMilestone().getVersion());
    }

    @Test
    void testGetAnalyzedArtifactsReturnsComplexDataset() {
        List<AnalyzedArtifact> artifacts = mockAdapter.getAnalyzedArtifacts("ANY-ID");

        assertNotNull(artifacts);
        assertEquals(4, artifacts.size(), "Mock should return exactly 4 carefully crafted test artifacts");

        // Verify the artifacts belong to the two expected deliverables
        long backendCount = artifacts.stream()
                .filter(a -> a.getDistribution().getDistributionUrl().contains("backend"))
                .count();
        long frontendCount = artifacts.stream()
                .filter(a -> a.getDistribution().getDistributionUrl().contains("frontend"))
                .count();

        assertEquals(3, backendCount, "Backend distribution should have 3 artifacts");
        assertEquals(1, frontendCount, "Frontend distribution should have 1 artifact");
    }

    @Test
    void testGetAnalyzedArtifactsContainsRichMetadata() {
        List<AnalyzedArtifact> artifacts = mockAdapter.getAnalyzedArtifacts("ANY-ID");

        // Extract the Red Hat Maven artifact
        AnalyzedArtifact rhMavenAnalyzed = artifacts.stream()
                .filter(a -> "art-1".equals(a.getArtifact().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Failed to find art-1"));

        Artifact art = rhMavenAnalyzed.getArtifact();

        // 1. Verify Hashes
        assertEquals("137a4f61625440544ab10746c07da73f", art.getMd5());
        assertEquals("4a742528932f05fcf2341de02d3a2e5c13d8d488", art.getSha1());
        assertEquals("1878c9a663bba7d063cf9a08f5d0968be68f70f4e4b086ef729d6f5d850b5fa6", art.getSha256());

        // 2. Verify Build Environment Meta
        Build build = art.getBuild();
        assertNotNull(build, "Build should not be null for RH artifact");
        assertNotNull(build.getEnvironment(), "Build Environment should be populated");
        assertEquals("quay.io/rh-newcastle/builder-rhel-7-j8-mvn3.6.3", build.getEnvironment().getSystemImageRepositoryUrl());
        assertEquals("1.0.0", build.getEnvironment().getSystemImageId());

        // 3. Verify VCS / Pedigree Information
        assertNotNull(build.getScmRepository(), "SCM Repository should be populated");
        assertEquals("https://github.com/acme/acme-core.git", build.getScmRepository().getExternalUrl());
        assertEquals("ec5af3461c0004d3f510b88b3abb34828206c2e4", build.getScmRevision());
        assertEquals("1.0.0.redhat-00001", build.getScmTag());
    }

    @Test
    void testGetNPMDependenciesTriggersWorkaround() {
        // Test Maven build ID (Triggers the workaround mock)
        List<Artifact> hiddenDeps = mockAdapter.getNPMDependencies("BUILD-9901");
        assertNotNull(hiddenDeps);
        assertEquals(1, hiddenDeps.size());
        assertEquals("lodash:4.17.21", hiddenDeps.get(0).getIdentifier());
        assertNotNull(hiddenDeps.get(0).getSha256(), "Hidden dependency should also include hashes");

        // Test NPM build ID (Should return empty)
        List<Artifact> noDeps = mockAdapter.getNPMDependencies("BUILD-9902");
        assertNotNull(noDeps);
        assertTrue(noDeps.isEmpty());
    }
}