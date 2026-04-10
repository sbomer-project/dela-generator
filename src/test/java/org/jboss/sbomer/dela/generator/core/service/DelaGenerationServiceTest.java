package org.jboss.sbomer.dela.generator.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;

import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.dto.response.AnalyzedDistribution;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;
import org.jboss.sbomer.dela.generator.core.port.spi.StatusUpdateService;
import org.jboss.sbomer.dela.generator.core.port.spi.StorageService;
import org.jboss.sbomer.events.orchestration.GenerationCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelaGenerationServiceTest {

    @Mock PNCService pncService;
    @Mock StorageService storageService;
    @Mock StatusUpdateService statusUpdateService;
    @Mock NpmDependencyWorkaroundService workaroundService;

    private DelaGenerationService delaService;

    @BeforeEach
    void setUp() {
        delaService = new DelaGenerationService(pncService, storageService, statusUpdateService, workaroundService);

        delaService.toolName = "TestTool";
        delaService.toolVersion = "1.0";
        delaService.supplierName = "TestSupplier";
        delaService.supplierUrls = List.of("https://test.com");
        delaService.pncApiUrl = "api.pnc.com";
    }

    @Test
    void testSuccessfulGenerationLifecycle() {
        // 1. Setup Mock Event using Deep Stubs
        GenerationCreated event = createMockEvent("GEN-123", "OP-456");

        // 2. Setup PNC Mocks
        DeliverableAnalyzerOperation op = DeliverableAnalyzerOperation.delAnalyzerBuilder().id("OP-456").build();
        when(pncService.getOperation("OP-456")).thenReturn(op);

        AnalyzedDistribution dist = AnalyzedDistribution.builder().distributionUrl("https://foo.com/app.zip").build();
        Artifact artifact = Artifact.builder().id("1").purl("pkg:maven/foo").build();
        AnalyzedArtifact analyzed = AnalyzedArtifact.builder().artifact(artifact).distribution(dist).build();
        when(pncService.getAnalyzedArtifacts("OP-456")).thenReturn(List.of(analyzed));

        when(storageService.uploadSbom(eq("GEN-123"), anyString())).thenReturn("http://storage/GEN-123.json");

        // 3. Execute
        delaService.processGeneration(event);

        // 4. Verify
        verify(statusUpdateService).reportGenerating("GEN-123");
        verify(workaroundService).applyWorkaround(any(), any(), anyString());

        ArgumentCaptor<List<String>> urlsCaptor = ArgumentCaptor.forClass(List.class);
        verify(statusUpdateService).reportFinished(eq("GEN-123"), urlsCaptor.capture());

        assertEquals(1, urlsCaptor.getValue().size());
        assertEquals("http://storage/GEN-123.json", urlsCaptor.getValue().get(0));
    }

    @Test
    void testFailureLifecycle() {
        // 1. Setup Mock Event
        GenerationCreated event = createMockEvent("GEN-999", "OP-BAD");

        // 2. Force Exception
        when(pncService.getOperation(anyString())).thenThrow(new RuntimeException("PNC is down!"));

        // 3. Execute & Assert Exception
        assertThrows(RuntimeException.class, () -> delaService.processGeneration(event));

        // 4. Verify Failure Routing
        verify(statusUpdateService).reportGenerating("GEN-999");
        verify(statusUpdateService).reportFailed(eq("GEN-999"), contains("PNC is down!"));
        verify(storageService, never()).uploadSbom(anyString(), anyString());
    }

    /**
     * Uses Mockito Deep Stubs to completely bypass the Avro builders.
     * This ensures the test is completely decoupled from schema builder changes.
     */
    private GenerationCreated createMockEvent(String genId, String targetId) {
        GenerationCreated event = mock(GenerationCreated.class, RETURNS_DEEP_STUBS);

        lenient().when(event.getData().getGenerationRequest().getGenerationId()).thenReturn(genId);
        lenient().when(event.getData().getGenerationRequest().getTarget().getIdentifier()).thenReturn(targetId);

        return event;
    }
}