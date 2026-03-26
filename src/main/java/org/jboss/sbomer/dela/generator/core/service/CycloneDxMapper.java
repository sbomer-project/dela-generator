package org.jboss.sbomer.dela.generator.core.service;

import java.util.ArrayList;
import java.util.List;

import org.cyclonedx.model.Component;
import org.cyclonedx.model.Hash;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.restclient.util.ArtifactUtil; // From rest-client-jakarta
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.commonjava.atlas.npm.ident.ref.NpmPackageRef;

public class CycloneDxMapper {

    public static Component mapArtifactToComponent(AnalyzedArtifact analyzed) {
        Component component = new Component();
        Artifact artifact = analyzed.getArtifact();

        // 1. Structural Identity
        component.setPurl(artifact.getPurl());
        component.setBomRef(artifact.getPurl());
        component.setType(Component.Type.LIBRARY);
        component.setScope(Component.Scope.REQUIRED);

        // 2. Coordinate Extraction (The "PNC Way")
        setCoordinates(component, artifact);

        // 3. Map Hashes
        List<Hash> hashes = new ArrayList<>();
        if (artifact.getMd5() != null) hashes.add(new Hash(Hash.Algorithm.MD5, artifact.getMd5()));
        if (artifact.getSha1() != null) hashes.add(new Hash(Hash.Algorithm.SHA1, artifact.getSha1()));
        if (artifact.getSha256() != null) hashes.add(new Hash(Hash.Algorithm.SHA_256, artifact.getSha256()));

        if (!hashes.isEmpty()) {
            component.setHashes(hashes);
        }

        return component;
    }

    private static void setCoordinates(Component component, Artifact artifact) {
        if (artifact.getTargetRepository() == null) {
            component.setName(artifact.getFilename());
            component.setVersion("unknown");
            return;
        }

        switch (artifact.getTargetRepository().getRepositoryType()) {
            case MAVEN:
                SimpleArtifactRef maven = ArtifactUtil.parseMavenCoordinates(artifact);
                component.setGroup(maven.getGroupId());
                component.setName(maven.getArtifactId());
                component.setVersion(maven.getVersionString());
                break;
            case NPM:
                NpmPackageRef npm = ArtifactUtil.parseNPMCoordinates(artifact);
                component.setName(npm.getName());
                component.setVersion(npm.getVersion().toString());
                break;
            default:
                component.setName(artifact.getFilename());
                component.setVersion("unknown");
        }
    }
}