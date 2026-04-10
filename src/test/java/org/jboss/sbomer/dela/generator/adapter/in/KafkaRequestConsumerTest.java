package org.jboss.sbomer.dela.generator.adapter.in;

import static org.jboss.sbomer.dela.generator.core.ApplicationConstants.COMPONENT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.jboss.sbomer.dela.generator.core.port.api.GenerationProcessor;
import org.jboss.sbomer.dela.generator.core.port.spi.FailureNotifier;
import org.jboss.sbomer.events.orchestration.GenerationCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaRequestConsumerTest {

    @Mock
    GenerationProcessor generationProcessor;

    @Mock
    FailureNotifier failureNotifier;

    @InjectMocks
    KafkaRequestConsumer consumer;

    private GenerationCreated validEvent;

    @BeforeEach
    void setUp() {
        validEvent = mock(GenerationCreated.class, RETURNS_DEEP_STUBS);

        // Added lenient() to stop Mockito from complaining if a test doesn't use these specific mocks
        lenient().when(validEvent.getContext().getEventId()).thenReturn("event-123");
        lenient().when(validEvent.getContext().getCorrelationId()).thenReturn("corr-456");
        lenient().when(validEvent.getData().getGenerationRequest().getGenerationId()).thenReturn("gen-789");
    }

    @Test
    void testReceiveProcessesEventForMyGenerator() {
        lenient().when(validEvent.getData().getRecipe().getGenerator().getName()).thenReturn(COMPONENT_NAME);

        consumer.receive(validEvent);

        verify(generationProcessor).processGeneration(validEvent);
        verify(failureNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void testReceiveIgnoresEventForOtherGenerators() {
        lenient().when(validEvent.getData().getRecipe().getGenerator().getName()).thenReturn("some-other-generator");

        consumer.receive(validEvent);

        verify(generationProcessor, never()).processGeneration(any());
        verify(failureNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void testReceiveCatchesExceptionsAndNotifiesFailure() {
        lenient().when(validEvent.getData().getRecipe().getGenerator().getName()).thenReturn(COMPONENT_NAME);
        RuntimeException ex = new RuntimeException("Simulated processing crash");
        doThrow(ex).when(generationProcessor).processGeneration(validEvent);

        consumer.receive(validEvent);

        verify(generationProcessor).processGeneration(validEvent);
        verify(failureNotifier).notify(any(), eq("corr-456"), eq(validEvent));
    }
}
