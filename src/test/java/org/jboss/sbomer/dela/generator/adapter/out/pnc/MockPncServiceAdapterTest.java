package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
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
    void testGetNPMDependenciesTriggersWorkaround() {
        // Test Maven build ID (Triggers the workaround mock)
        List<Artifact> hiddenDeps = mockAdapter.getNPMDependencies("BUILD-9901");
        assertNotNull(hiddenDeps);
        assertEquals(1, hiddenDeps.size());
        assertEquals("lodash:4.17.21", hiddenDeps.get(0).getIdentifier());

        // Test NPM build ID (Should return empty)
        List<Artifact> noDeps = mockAdapter.getNPMDependencies("BUILD-9902");
        assertNotNull(noDeps);
        assertTrue(noDeps.isEmpty());
    }
}