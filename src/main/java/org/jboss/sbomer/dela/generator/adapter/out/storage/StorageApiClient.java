package org.jboss.sbomer.dela.generator.adapter.out.storage;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

@RegisterRestClient(configKey = "storage-api")
@Path("/api/v1/storage")
public interface StorageApiClient {

    @POST
    @Path("/generations/{generationId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Map<String, String> uploadFile(
            @PathParam("generationId") String generationId,
            MultipartFormDataOutput form
    );
}