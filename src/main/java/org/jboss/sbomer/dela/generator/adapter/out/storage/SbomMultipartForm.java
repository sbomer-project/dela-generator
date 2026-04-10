package org.jboss.sbomer.dela.generator.adapter.out.storage;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;

import java.io.File;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

public class SbomMultipartForm {

    // Matches the @RestForm("files") expected by the Manifest Storage Service
    @FormParam("files")
    @PartType(MediaType.APPLICATION_JSON)
    public File file;

    public SbomMultipartForm() {
    }

    public SbomMultipartForm(File file) {
        this.file = file;
    }
}
