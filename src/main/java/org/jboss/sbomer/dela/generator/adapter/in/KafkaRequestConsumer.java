package org.jboss.sbomer.dela.generator.adapter.in;

import static org.jboss.sbomer.dela.generator.core.ApplicationConstants.COMPONENT_NAME;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.sbomer.dela.generator.core.port.api.GenerationProcessor;
import org.jboss.sbomer.dela.generator.core.port.spi.FailureNotifier;
import org.jboss.sbomer.dela.generator.core.utility.FailureUtility;
import org.jboss.sbomer.events.orchestration.GenerationCreated;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class KafkaRequestConsumer {

    @Inject
    GenerationProcessor generationProcessor;

    @Inject
    FailureNotifier failureNotifier;

    @Incoming("generation-created")
    public void receive(GenerationCreated event) {
        try {
            log.debug("Received event ID: {}", event.getContext().getEventId());
            if (isMyGenerator(event)) {
                log.info("{} received task for generation: {}", COMPONENT_NAME,
                        event.getData().getGenerationRequest().getGenerationId());
                
                // Add traceParent to MDC or pass it down if needed, then call core logic
                generationProcessor.processGeneration(event);
            }
        } catch (Exception e) {
            // Catch exceptions so we don't crash the consumer loop.
            log.error("Skipping malformed or incompatible event: {}", event, e);
            Span span = Span.current();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            
            if (event != null) {
                failureNotifier.notify(FailureUtility.buildFailureSpecFromException(e), event.getContext().getCorrelationId(), event);
            } else {
                failureNotifier.notify(FailureUtility.buildFailureSpecFromException(e), null, null);
            }
        }
    }

    private boolean isMyGenerator(GenerationCreated event) {
        // Safety checks to prevent NPEs
        if (event.getData() == null
                || event.getData().getRecipe() == null
                || event.getData().getRecipe().getGenerator() == null) {
            return false;
        }
        return COMPONENT_NAME.equals(event.getData().getRecipe().getGenerator().getName());
    }
}
