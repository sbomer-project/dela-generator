package org.jboss.sbomer.dela.generator.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.commonjava.atlas.npm.ident.ref.NpmPackageRef;
import org.cyclonedx.model.Ancestors;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.model.OrganizationalEntity;
import org.jboss.pnc.api.deliverablesanalyzer.dto.LicenseInfo;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.restclient.util.ArtifactUtil;
import org.jboss.sbomer.dela.generator.core.utility.VcsUrl;

import com.github.packageurl.PackageURL;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CycloneDxMapper {

    private static final String SBOM_RED_HAT_PNC_ARTIFACT_ID = "pnc-artifact-id";
    private static final String SBOM_RED_HAT_PNC_BUILD_ID = "pnc-build-id";
    private static final String SBOM_RED_HAT_ENVIRONMENT_IMAGE = "pnc-environment-image";
    private static final String PUBLISHER = "Red Hat";
    private static final String MRRC_URL = "https://maven.repository.redhat.com/ga/";

    private static final Pattern RH_VERSION_PATTERN = Pattern.compile(".*redhat-.*");
    private static final Pattern RH_NPM_PURL_PATTERN = Pattern.compile(".*(?:%40redhat|@redhat).*");

    public static Component mapArtifactToComponent(AnalyzedArtifact analyzed, String pncApiUrl, String resolvedPurl) {
        Component component = new Component();
        Artifact artifact = analyzed.getArtifact();

        // Structural Identity (Using the resolvedPurl)
        component.setPurl(resolvedPurl);
        component.setBomRef(resolvedPurl);
        component.setType(Component.Type.LIBRARY);
        component.setScope(Component.Scope.REQUIRED);

        // Coordinate Extraction (With NPE Safety)
        setCoordinates(component, artifact);

        // Map Hashes
        List<Hash> hashes = new ArrayList<>();
        if (artifact.getMd5() != null) hashes.add(new Hash(Hash.Algorithm.MD5, artifact.getMd5()));
        if (artifact.getSha1() != null) hashes.add(new Hash(Hash.Algorithm.SHA1, artifact.getSha1()));
        if (artifact.getSha256() != null) hashes.add(new Hash(Hash.Algorithm.SHA_256, artifact.getSha256()));
        if (!hashes.isEmpty()) component.setHashes(hashes);

        // Map Licenses
        mapLicenses(component, analyzed.getLicenses());

        // Map External References (PNC Traceability)
        mapPncTraceability(component, artifact, pncApiUrl);

        // Map Source Pedigree (Ancestors)
        mapPedigree(component, artifact);

        // Apply Red Hat Specific Tagging
        if (isRhComponent(component)) {
            applyRedHatMetadata(component);
        }

        return component;
    }

    private static void setCoordinates(Component component, Artifact artifact) {
        if (artifact.getTargetRepository() == null) {
            fallbackToFilename(component, artifact);
            return;
        }

        try {
            switch (artifact.getTargetRepository().getRepositoryType()) {
                case MAVEN:
                    SimpleArtifactRef maven = ArtifactUtil.parseMavenCoordinates(artifact);
                    if (maven != null) {
                        component.setGroup(maven.getGroupId());
                        component.setName(maven.getArtifactId());
                        component.setVersion(maven.getVersionString());
                    } else {
                        parseMavenFallback(component, artifact);
                    }
                    break;
                case NPM:
                    if (artifact.getIdentifier() != null && !artifact.getIdentifier().isEmpty()) {
                        NpmPackageRef npm = ArtifactUtil.parseNPMCoordinates(artifact);
                        if (npm != null) {
                            component.setName(npm.getName());
                            component.setVersion(npm.getVersion().getNormalVersion());
                        } else {
                            fallbackToFilename(component, artifact);
                        }
                    } else {
                        log.warn("NPM artifact missing identifier, falling back to filename: {}", artifact.getId());
                        fallbackToFilename(component, artifact);
                    }
                    break;
                default:
                    fallbackToFilename(component, artifact);
            }
        } catch (Exception e) {
            log.warn("Failed to parse coordinates for artifact ID: {}, falling back to filename. Error: {}",
                    artifact.getId(), e.getMessage());
            fallbackToFilename(component, artifact);
        }
    }

    // Custom fallback if PNC's ArtifactUtil fails (e.g. missing classpath dependencies)
    private static void parseMavenFallback(Component component, Artifact artifact) {
        if (artifact.getIdentifier() != null) {
            String[] parts = artifact.getIdentifier().split(":");
            if (parts.length >= 4) {
                component.setGroup(parts[0]);
                component.setName(parts[1]);
                component.setVersion(parts[3]);
                return;
            }
        }
        fallbackToFilename(component, artifact);
    }

    private static void fallbackToFilename(Component component, Artifact artifact) {
        component.setName(artifact.getFilename() != null ? artifact.getFilename() : "unknown-artifact-" + artifact.getId());
        component.setVersion("unknown");
    }

    private static void mapLicenses(Component component, Set<LicenseInfo> licenseInfos) {
        if (licenseInfos == null || licenseInfos.isEmpty()) {
            return;
        }

        LicenseChoice choice = new LicenseChoice();
        List<License> licenses = new ArrayList<>();

        for (LicenseInfo info : licenseInfos) {
            License l = new License();
            l.setAcknowledgement("concluded");

            if (info.getSpdxLicenseId() != null && !info.getSpdxLicenseId().isEmpty()) {
                l.setId(info.getSpdxLicenseId());
            } else {
                l.setName("Unknown");
            }

            if (info.getUrl() != null && !info.getUrl().isEmpty()) {
                l.setUrl(info.getUrl());
            }
            licenses.add(l);
        }

        choice.setLicenses(licenses);
        component.setLicenses(choice);
    }

    private static boolean isRhComponent(Component component) {
        boolean isRhVersion = component.getVersion() != null && RH_VERSION_PATTERN.matcher(component.getVersion()).matches();
        boolean isRhPurl = component.getPurl() != null && RH_NPM_PURL_PATTERN.matcher(component.getPurl()).matches();
        return isRhVersion || isRhPurl;
    }

    private static void applyRedHatMetadata(Component component) {
        component.setPublisher(PUBLISHER);

        OrganizationalEntity org = new OrganizationalEntity();
        org.setName(PUBLISHER);
        org.setUrls(List.of("https://www.redhat.com"));
        component.setSupplier(org);

        addExternalReference(component, ExternalReference.Type.DISTRIBUTION, MRRC_URL, null);
    }

    private static void mapPncTraceability(Component component, Artifact artifact, String pncApiUrl) {
        addExternalReference(component, ExternalReference.Type.BUILD_SYSTEM,
                pncApiUrl + "/pnc-rest/v2/artifacts/" + artifact.getId(),
                SBOM_RED_HAT_PNC_ARTIFACT_ID);

        Build build = artifact.getBuild();
        if (build != null) {
            addExternalReference(component, ExternalReference.Type.BUILD_SYSTEM,
                    pncApiUrl + "/pnc-rest/v2/builds/" + build.getId(),
                    SBOM_RED_HAT_PNC_BUILD_ID);

            if (build.getEnvironment() != null) {
                addExternalReference(component, ExternalReference.Type.BUILD_META,
                        build.getEnvironment().getSystemImageRepositoryUrl() + "/" + build.getEnvironment().getSystemImageId(),
                        SBOM_RED_HAT_ENVIRONMENT_IMAGE);
            }

            if (build.getScmRepository() != null && build.getScmRepository().getExternalUrl() != null) {
                addExternalReference(component, ExternalReference.Type.VCS,
                        build.getScmRepository().getExternalUrl(), null);
            }
        }
    }

    private static void mapPedigree(Component component, Artifact artifact) {
        Build build = artifact.getBuild();
        if (build == null) return;

        List<Component> ancestorsList = new ArrayList<>();
        List<org.cyclonedx.model.Commit> commitsList = new ArrayList<>();

        // 1. Internal SCM
        if (build.getScmUrl() != null && build.getScmRevision() != null) {
            String fullUrl = build.getScmTag() != null && !build.getScmTag().isBlank()
                    ? build.getScmUrl() + "#" + build.getScmTag()
                    : build.getScmUrl();

            // Add as Ancestor (Modern SBOMer format)
            addPedigreeAncestor(ancestorsList, component.getName(), fullUrl, build.getScmRevision());

            // Add as Commit (Legacy SBOMer format)
            org.cyclonedx.model.Commit commit = new org.cyclonedx.model.Commit();
            commit.setUid(build.getScmRevision());
            commit.setUrl(fullUrl);
            commitsList.add(commit);
        }

        // 2. External SCM (if applicable)
        if (build.getScmRepository() != null && build.getScmRepository().getExternalUrl() != null
                && build.getScmBuildConfigRevision() != null
                && build.getBuildConfigRevision() != null) {

            String fullUrl = build.getScmRepository().getExternalUrl() + "#" + build.getBuildConfigRevision().getScmRevision();

            // Add as Ancestor
            addPedigreeAncestor(ancestorsList, component.getName(), fullUrl, build.getScmBuildConfigRevision());

            // Add as Commit
            org.cyclonedx.model.Commit commit = new org.cyclonedx.model.Commit();
            commit.setUid(build.getScmBuildConfigRevision());
            commit.setUrl(fullUrl);
            commitsList.add(commit);
        }

        // Attach Pedigree to Component
        if (!ancestorsList.isEmpty() || !commitsList.isEmpty()) {
            org.cyclonedx.model.Pedigree pedigree = new org.cyclonedx.model.Pedigree();

            if (!ancestorsList.isEmpty()) {
                org.cyclonedx.model.Ancestors ancestors = new org.cyclonedx.model.Ancestors();
                ancestors.setComponents(ancestorsList);
                pedigree.setAncestors(ancestors);
            }

            if (!commitsList.isEmpty()) {
                pedigree.setCommits(commitsList);
            }

            component.setPedigree(pedigree);
        }
    }

    private static void addPedigreeAncestor(List<Component> ancestorsList, String fallbackName, String fullUrl, String commitHash) {
        if (fullUrl == null || fullUrl.isBlank()) return;

        try {
            // Use the battle-tested legacy VcsUrl class to handle JGit parsing and PackageURL extraction
            VcsUrl vcsUrl = VcsUrl.create(fullUrl);
            PackageURL packageURL = vcsUrl.toPackageURL(commitHash);

            Component ancestor = new Component();
            ancestor.setType(Component.Type.LIBRARY);
            ancestor.setName(packageURL.getName());
            ancestor.setVersion(commitHash);

            String purl = packageURL.toString();
            ancestor.setPurl(purl);
            ancestor.setBomRef(purl);

            ancestorsList.add(ancestor);
        } catch (Exception e) {
            log.warn("Error creating pedigree purl for URL '{}': {}", fullUrl, e.getMessage());

            // Graceful fallback to ensure the ancestor is at least recorded even if parsing fails
            Component ancestor = new Component();
            ancestor.setType(Component.Type.LIBRARY);
            ancestor.setName(fallbackName);
            ancestor.setVersion(commitHash);
            ancestorsList.add(ancestor);
        }
    }

    private static void addExternalReference(Component component, ExternalReference.Type type, String url, String comment) {
        if (url == null || url.isBlank()) return;

        List<ExternalReference> refs = component.getExternalReferences() != null
                ? new ArrayList<>(component.getExternalReferences())
                : new ArrayList<>();

        ExternalReference ref = new ExternalReference();
        ref.setType(type);
        ref.setUrl(url);
        if (comment != null) ref.setComment(comment);

        refs.add(ref);
        component.setExternalReferences(refs);
    }
}
