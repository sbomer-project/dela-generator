package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.DeliverableAnalyzerReportClient;
import org.jboss.pnc.client.OperationClient;
import org.jboss.pnc.client.RemoteCollection;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PncServiceAdapterTest {

    @Mock OperationClient mockOperationClient;
    @Mock DeliverableAnalyzerReportClient mockReportClient;
    @Mock BuildClient mockBuildClient;

    private PncServiceAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new PncServiceAdapter();
        adapter.pncApiUrl = "http://dummy-pnc.com";

        setPrivateField(adapter, "operationClient", mockOperationClient);
        setPrivateField(adapter, "reportClient", mockReportClient);
        setPrivateField(adapter, "buildClient", mockBuildClient);
    }

    @Test
    void testGetOperationSuccess() throws Exception {
        DeliverableAnalyzerOperation expectedOp = DeliverableAnalyzerOperation.delAnalyzerBuilder().id("123").build();
        when(mockOperationClient.getSpecificDeliverableAnalyzer("123")).thenReturn(expectedOp);

        DeliverableAnalyzerOperation result = adapter.getOperation("123");

        assertNotNull(result);
        assertEquals("123", result.getId());
    }

    @Test
    void testGetOperationReturnsNullOnNotFound() throws Exception {
        // Mock the exception directly to avoid JAX-RS / Jersey classloading errors
        RemoteResourceNotFoundException ex = mock(RemoteResourceNotFoundException.class);
        when(mockOperationClient.getSpecificDeliverableAnalyzer("999")).thenThrow(ex);

        DeliverableAnalyzerOperation result = adapter.getOperation("999");

        assertNull(result, "Should gracefully return null when operation is not found");
    }

    @Test
    void testGetAnalyzedArtifactsSuccess() throws Exception {
        AnalyzedArtifact artifact = AnalyzedArtifact.builder().builtFromSource(true).build();
        RemoteCollection<AnalyzedArtifact> remoteCollection = mock(RemoteCollection.class);
        when(remoteCollection.getAll()).thenReturn(List.of(artifact));

        when(mockReportClient.getAnalyzedArtifacts("123")).thenReturn(remoteCollection);

        List<AnalyzedArtifact> results = adapter.getAnalyzedArtifacts("123");

        assertEquals(1, results.size());
        assertTrue(results.get(0).isBuiltFromSource());
    }

    @Test
    void testGetAnalyzedArtifactsThrowsExceptionOnNotFound() throws Exception {
        RemoteResourceNotFoundException ex = mock(RemoteResourceNotFoundException.class);
        when(mockReportClient.getAnalyzedArtifacts("999")).thenThrow(ex);

        RuntimeException thrownEx = assertThrows(RuntimeException.class, () -> adapter.getAnalyzedArtifacts("999"));
        assertTrue(thrownEx.getMessage().contains("Analyzed artifacts not found"));
    }

    @Test
    void testGetNPMDependenciesSuccess() throws Exception {
        Artifact npmDep = Artifact.builder().id("npm-1").purl("pkg:npm/lodash").build();
        RemoteCollection<Artifact> remoteCollection = mock(RemoteCollection.class);
        when(remoteCollection.getAll()).thenReturn(List.of(npmDep));

        when(mockBuildClient.getDependencyArtifacts(eq("B-123"), any(Optional.class), eq(Optional.of("purl=LIKE=pkg:npm*"))))
                .thenReturn(remoteCollection);

        List<Artifact> results = adapter.getNPMDependencies("B-123");

        assertEquals(1, results.size());
        assertEquals("pkg:npm/lodash", results.get(0).getPurl());
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}