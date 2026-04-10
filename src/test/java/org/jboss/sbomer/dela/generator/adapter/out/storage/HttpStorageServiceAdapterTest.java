package org.jboss.sbomer.dela.generator.adapter.out.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
import org.jboss.sbomer.dela.generator.adapter.out.storage.exception.SBOMUploadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpStorageServiceAdapterTest {

    @Mock
    StorageApiClient storageClient;

    @InjectMocks
    HttpStorageServiceAdapter adapter;

    @Test
    void testUploadSbomSuccess() {
        // Arrange
        String generationId = "GEN-777";
        String sbomContent = "{\"bomFormat\": \"CycloneDX\"}";
        
        // Storage API returns a map of filenames to URLs
        Map<String, String> mockedResponse = Map.of(
            "bom-12345.json", "http://storage.local/api/v1/sboms/bom-12345.json"
        );
        when(storageClient.uploadFile(eq(generationId), any(MultipartFormDataOutput.class)))
            .thenReturn(mockedResponse);

        // Act
        String resultUrl = adapter.uploadSbom(generationId, sbomContent);

        // Assert
        assertEquals("http://storage.local/api/v1/sboms/bom-12345.json", resultUrl);
    }

    @Test
    void testUploadSbomThrowsExceptionOnMissingJsonUrl() {
        // Arrange
        String generationId = "GEN-888";
        String sbomContent = "{}";
        
        // Mock a response that doesn't contain a .json file key
        Map<String, String> invalidResponse = Map.of(
            "bom-12345.xml", "http://storage.local/bom.xml"
        );
        when(storageClient.uploadFile(eq(generationId), any(MultipartFormDataOutput.class)))
            .thenReturn(invalidResponse);

        // Act & Assert
        SBOMUploadException ex = assertThrows(SBOMUploadException.class, () -> 
            adapter.uploadSbom(generationId, sbomContent)
        );
        assertTrue(ex.getMessage().contains("Storage service did not return a valid URL"));
    }

    @Test
    void testUploadSbomHandlesRestClientFailure() {
        // Arrange
        String generationId = "GEN-999";
        
        // Mock the client blowing up entirely
        when(storageClient.uploadFile(eq(generationId), any(MultipartFormDataOutput.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        SBOMUploadException ex = assertThrows(SBOMUploadException.class, () -> 
            adapter.uploadSbom(generationId, "{}")
        );
        assertTrue(ex.getMessage().contains("Error uploading SBOM: Connection refused"));
    }
}