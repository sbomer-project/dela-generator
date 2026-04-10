package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;

/**
 * Fallback Quarkus REST Client for PNC.
 * Use this when the official org.jboss.pnc.client.* library lacks support for an endpoint.
 */
@RegisterRestClient(configKey = "pnc-api")
@Path("/pnc-rest/v2")
@Produces(MediaType.APPLICATION_JSON)
public interface PncRestApiClient {

    // Example of an endpoint mapped here just in case!
    @GET
    @Path("/deliverable-analyses/{operationId}")
    DeliverableAnalyzerOperation getOperationFallback(@PathParam("operationId") String operationId);

}
