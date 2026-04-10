package org.jboss.sbomer.dela.generator.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.sbomer.events.generator.GenerationUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaStatusUpdateServiceTest {

    @Mock
    Emitter<GenerationUpdate> emitter;

    @InjectMocks
    KafkaStatusUpdateService statusUpdateService;

    @BeforeEach
    void setUp() {
        // Ensure the emitter doesn't throw NPE on .whenComplete() by returning a completed future
        when(emitter.send(any(GenerationUpdate.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void testReportGenerating() {
        // Act
        statusUpdateService.reportGenerating("GEN-100");

        // Assert
        ArgumentCaptor<GenerationUpdate> captor = ArgumentCaptor.forClass(GenerationUpdate.class);
        verify(emitter).send(captor.capture());

        GenerationUpdate sentEvent = captor.getValue();
        assertEquals("dela-generator", sentEvent.getContext().getSource());
        assertEquals("GEN-100", sentEvent.getData().getGenerationId());
        assertEquals("GENERATING", sentEvent.getData().getStatus());
        assertEquals(0, sentEvent.getData().getResultCode());
    }

    @Test
    void testReportFinished() {
        // Act
        List<String> urls = List.of("http://url1.com", "http://url2.com");
        statusUpdateService.reportFinished("GEN-200", urls);

        // Assert
        ArgumentCaptor<GenerationUpdate> captor = ArgumentCaptor.forClass(GenerationUpdate.class);
        verify(emitter).send(captor.capture());

        GenerationUpdate sentEvent = captor.getValue();
        assertEquals("GEN-200", sentEvent.getData().getGenerationId());
        assertEquals("FINISHED", sentEvent.getData().getStatus());
        assertEquals(urls, sentEvent.getData().getBaseSbomUrls());
        assertEquals(0, sentEvent.getData().getResultCode());
    }

    @Test
    void testReportFailed() {
        // Act
        statusUpdateService.reportFailed("GEN-300", "Something blew up");

        // Assert
        ArgumentCaptor<GenerationUpdate> captor = ArgumentCaptor.forClass(GenerationUpdate.class);
        verify(emitter).send(captor.capture());

        GenerationUpdate sentEvent = captor.getValue();
        assertEquals("GEN-300", sentEvent.getData().getGenerationId());
        assertEquals("FAILED", sentEvent.getData().getStatus());
        assertEquals("Something blew up", sentEvent.getData().getReason());
        assertEquals(1, sentEvent.getData().getResultCode()); // Failure code
    }
}
