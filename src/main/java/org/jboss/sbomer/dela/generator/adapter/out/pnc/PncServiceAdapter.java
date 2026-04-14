package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.pnc.client.BuildClient;
import org.jboss.pnc.client.Configuration;
import org.jboss.pnc.client.DeliverableAnalyzerReportClient;
import org.jboss.pnc.client.OperationClient;
import org.jboss.pnc.client.RemoteResourceNotFoundException;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class PncServiceAdapter implements PNCService {

    @ConfigProperty(name = "pnc.api.url")
    String pncApiUrl;

    // --- 1. The Fallback REST Client ---
    @Inject
    @RestClient
    PncRestApiClient pncRestClient;

    // --- 2. The Official Library Clients ---
    private OperationClient operationClient;
    private DeliverableAnalyzerReportClient reportClient;
    private BuildClient buildClient;

    @PostConstruct
    void init() {
        log.info("Initializing official PNC Clients with URL: {}", pncApiUrl);
        Configuration config = Configuration.builder().host(pncApiUrl).protocol("http").build();

        this.operationClient = new OperationClient(config);
        this.reportClient = new DeliverableAnalyzerReportClient(config);
        this.buildClient = new BuildClient(config);
    }

    @PreDestroy
    void cleanup() {
        if (operationClient != null) operationClient.close();
        if (reportClient != null) reportClient.close();
        if (buildClient != null) buildClient.close();
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public DeliverableAnalyzerOperation getOperation(@SpanAttribute("pnc.operationId") String operationId) {
        log.debug("Fetching DeliverableAnalyzerOperation '{}'", operationId);
        try {
            return operationClient.getSpecificDeliverableAnalyzer(operationId);
        } catch (RemoteResourceNotFoundException e) {
            log.warn("DeliverableAnalyzerOperation with id '{}' was not found", operationId);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve operation from PNC", e);
        }
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public List<AnalyzedArtifact> getAnalyzedArtifacts(@SpanAttribute("pnc.operationId") String operationId) {
        log.debug("Fetching AnalyzedArtifacts for operation '{}'", operationId);
        try {
            var remoteCollection = reportClient.getAnalyzedArtifacts(operationId);
            return new ArrayList<>(remoteCollection.getAll());
        } catch (RemoteResourceNotFoundException e) {
            throw new RuntimeException("Analyzed artifacts not found for operation " + operationId, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve analyzed artifacts from PNC", e);
        }
    }

    @Override
    @WithSpan
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public List<Artifact> getNPMDependencies(@SpanAttribute("pnc.buildId") String buildId) {
        log.debug("Fetching NPM Dependencies for build '{}'", buildId);
        try {
            var remoteCollection = buildClient.getDependencyArtifacts(
                    buildId, Optional.empty(), Optional.of("purl=LIKE=pkg:npm*"));
            return new ArrayList<>(remoteCollection.getAll());
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve NPM dependencies for build " + buildId, e);
        }
    }
}
