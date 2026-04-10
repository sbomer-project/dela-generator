package org.jboss.sbomer.dela.generator.adapter.out.pnc;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.jboss.pnc.api.deliverablesanalyzer.dto.LicenseInfo;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfigurationRevision;
import org.jboss.pnc.dto.DeliverableAnalyzerOperation;
import org.jboss.pnc.dto.Environment;
import org.jboss.pnc.dto.ProductMilestoneRef;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.dto.TargetRepository;
import org.jboss.pnc.dto.response.AnalyzedArtifact;
import org.jboss.pnc.dto.response.AnalyzedDistribution;
import org.jboss.pnc.enums.BuildType;
import org.jboss.pnc.enums.RepositoryType;
import org.jboss.sbomer.dela.generator.core.port.spi.PNCService;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.profile.IfBuildProfile;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@IfBuildProfile("mock")
@Alternative
@Priority(1)
@Slf4j
public class MockPncServiceAdapter implements PNCService {

    private static final String DIST_BACKEND_URL = "https://download.redhat.com/acme/acme-backend-1.0.0.zip";
    private static final String DIST_FRONTEND_URL = "https://download.redhat.com/acme/acme-frontend-1.0.0.tgz";

    private static final String MOCK_MAVEN_BUILD_ID = "BUILD-9901";
    private static final String MOCK_NPM_BUILD_ID = "BUILD-9902";

    @Override
    @WithSpan("PNC.getOperation (Mock)")
    public DeliverableAnalyzerOperation getOperation(@SpanAttribute("pnc.operationId") String operationId) {
        log.warn("🛡️ MOCK PNC: Providing Operation metadata");

        ProductMilestoneRef milestone = ProductMilestoneRef.refBuilder()
                .id("1160")
                .version("1.0.0.GA")
                .build();

        return DeliverableAnalyzerOperation.delAnalyzerBuilder()
                .id(operationId != null ? operationId : "MOCK-OP-123")
                .productMilestone(milestone)
                .startTime(Instant.now())
                .build();
    }

    @Override
    @WithSpan("PNC.getAnalyzedArtifacts (Mock)")
    public List<AnalyzedArtifact> getAnalyzedArtifacts(@SpanAttribute("pnc.operationId") String operationId) {
        log.warn("🛡️ MOCK PNC: Providing complex fake AnalyzedArtifacts");

        // --- DISTRIBUTIONS ---
        AnalyzedDistribution backendDist = AnalyzedDistribution.builder()
                .distributionUrl(DIST_BACKEND_URL)
                .creationTime(new Date())
                .sha256("aaaa24ef30e4e6bd5dda25a3e347fd244b4ee106f751603867019871e34caaaa")
                .build();

        AnalyzedDistribution frontendDist = AnalyzedDistribution.builder()
                .distributionUrl(DIST_FRONTEND_URL)
                .creationTime(new Date())
                .sha256("bbbb24ef30e4e6bd5dda25a3e347fd244b4ee106f751603867019871e34cbbbb")
                .build();

        // --- REPOSITORIES ---
        TargetRepository mavenRepo = TargetRepository.refBuilder()
                .id("1001")
                .identifier("indy-maven")
                .repositoryType(RepositoryType.MAVEN)
                .build();

        TargetRepository npmRepo = TargetRepository.refBuilder()
                .id("1002")
                .identifier("indy-npm")
                .repositoryType(RepositoryType.NPM)
                .build();

        // --- LICENSES ---
        LicenseInfo apache2 = LicenseInfo.builder().spdxLicenseId("Apache-2.0").name("Apache 2.0").build();
        LicenseInfo mit = LicenseInfo.builder().spdxLicenseId("MIT").name("MIT License").build();

        // --- BUILD METADATA (Environment & VCS for Pedigree/Traceability) ---
        Environment mockEnv = Environment.builder()
                .systemImageRepositoryUrl("quay.io/rh-newcastle/builder-rhel-7-j8-mvn3.6.3")
                .systemImageId("1.0.0")
                .build();

        SCMRepository mockScmRepo = SCMRepository.builder()
                .id("repo-1")
                .internalUrl("git+ssh://code.engineering.redhat.com/acme/acme-core.git")
                .externalUrl("https://github.com/acme/acme-core.git")
                .build();

        Build rhMavenBuild = Build.builder()
                .id(MOCK_MAVEN_BUILD_ID)
                .environment(mockEnv)
                .scmRepository(mockScmRepo)
                .scmUrl("https://gitlab.cee.redhat.com/acme/acme-core.git")
                .scmRevision("ec5af3461c0004d3f510b88b3abb34828206c2e4")
                .scmTag("1.0.0.redhat-00001")
                .scmBuildConfigRevision("dbbdb0330c0a94d90656a1aa95c1510c")
                .buildConfigRevision(BuildConfigurationRevision.builder()
                        .buildType(BuildType.MVN)
                        .scmRevision("dbbdb0330c0a94d90656a1aa95c1510c")
                        .build())
                .build();

        // --- ARTIFACT 1: Red Hat Maven Build (Includes Hashes & Rich Build Meta) ---
        Artifact rhMavenArtifact = Artifact.builder()
                .id("art-1")
                .identifier("com.redhat.acme:acme-core:jar:1.0.0.redhat-00001")
                .purl("pkg:maven/com.redhat.acme/acme-core@1.0.0.redhat-00001?type=jar")
                .filename("acme-core-1.0.0.redhat-00001.jar")
                .md5("137a4f61625440544ab10746c07da73f")
                .sha1("4a742528932f05fcf2341de02d3a2e5c13d8d488")
                .sha256("1878c9a663bba7d063cf9a08f5d0968be68f70f4e4b086ef729d6f5d850b5fa6")
                .targetRepository(mavenRepo)
                .build(rhMavenBuild)
                .build();

        AnalyzedArtifact analyzedRhMaven = AnalyzedArtifact.builder()
                .builtFromSource(true)
                .artifact(rhMavenArtifact)
                .distribution(backendDist)
                .licenses(Set.of(apache2))
                .archiveFilenames(List.of("acme-backend-1.0.0.zip!/lib/acme-core.jar"))
                .build();

        // --- ARTIFACT 2: Upstream Maven Dependency (Should NOT get RH metadata) ---
        Artifact upstreamMavenArtifact = Artifact.builder()
                .id("art-2")
                .identifier("com.fasterxml.jackson.core:jackson-core:jar:2.15.0")
                .purl("pkg:maven/com.fasterxml.jackson.core/jackson-core@2.15.0?type=jar")
                .filename("jackson-core-2.15.0.jar")
                .md5("11c7a27fc3cb10c663bf1257abce6ea2")
                .sha1("00a0c4edc12ac11aacb440c9514c7a9e4c43b873")
                .sha256("2aca80114732a342884fc024d17ea23d97562b40bf8a0babf0151d76e2ac01c7")
                .targetRepository(mavenRepo)
                .build(); // No build attached!

        AnalyzedArtifact analyzedUpstreamMaven = AnalyzedArtifact.builder()
                .builtFromSource(false)
                .artifact(upstreamMavenArtifact)
                .distribution(backendDist)
                .licenses(Set.of(apache2))
                .archiveFilenames(List.of("acme-backend-1.0.0.zip!/lib/jackson-core.jar"))
                .build();

        // --- ARTIFACT 3: Bad Artifact (Tests NPE safety & Fallback to filename) ---
        Artifact badArtifact = Artifact.builder()
                .id("art-3")
                .identifier("some-weird-binary.bin")
                .filename("some-weird-binary.bin")
                // No targetRepository, no PURL, no hashes
                .build();

        AnalyzedArtifact analyzedBad = AnalyzedArtifact.builder()
                .builtFromSource(false)
                .artifact(badArtifact)
                .distribution(backendDist)
                .archiveFilenames(List.of("acme-backend-1.0.0.zip!/bin/some-weird-binary.bin"))
                .build();

        // --- ARTIFACT 4: Red Hat NPM Build (Second Deliverable) ---
        Artifact rhNpmArtifact = Artifact.builder()
                .id("art-4")
                .identifier("@redhat/acme-ui:1.0.0-2")
                .purl("pkg:npm/%40redhat/acme-ui@1.0.0-2")
                .filename("acme-ui-1.0.0-2.tgz")
                .md5("7256f616cf783d1480afd3fb523c1389")
                .sha1("6be43fcb2bab757df3c6a73b2a5046fc8c85d9ac")
                .sha256("768de747afe2ba48b4185936158dbfb8e421e266db009ce03716f93896a950cd")
                .targetRepository(npmRepo)
                .build(Build.builder()
                        .id(MOCK_NPM_BUILD_ID)
                        .buildConfigRevision(BuildConfigurationRevision.builder().buildType(BuildType.NPM).build())
                        .build())
                .build();

        AnalyzedArtifact analyzedNpm = AnalyzedArtifact.builder()
                .builtFromSource(true)
                .artifact(rhNpmArtifact)
                .distribution(frontendDist)
                .licenses(Set.of(mit))
                .archiveFilenames(List.of("acme-frontend-1.0.0.tgz!/acme-ui.tgz"))
                .build();

        return List.of(analyzedRhMaven, analyzedUpstreamMaven, analyzedBad, analyzedNpm);
    }

    @Override
    @WithSpan("PNC.getNPMDependencies (Mock)")
    public List<Artifact> getNPMDependencies(@SpanAttribute("pnc.buildId") String buildId) {
        if (MOCK_MAVEN_BUILD_ID.equals(buildId)) {
            log.warn("🛡️ MOCK PNC: Injecting hidden NPM dependency for Maven Build {}", buildId);

            TargetRepository sharedRepo = TargetRepository.refBuilder()
                    .id("1003")
                    .identifier("indy-npm-shared")
                    .repositoryType(RepositoryType.NPM)
                    .build();

            Artifact hiddenDep = Artifact.builder()
                    .id("art-5-hidden")
                    .identifier("lodash:4.17.21")
                    .purl("pkg:npm/lodash@4.17.21")
                    .filename("lodash-4.17.21.tgz")
                    .md5("099d19965775e4b30c5aabeaaa9b8973")
                    .sha1("49ea128d047901590977ed88506f0b1d83fd509e")
                    .sha256("919a09fb019f2d71665900d6650d040dce1152bcd19622b83867da1b2812898a")
                    .targetRepository(sharedRepo)
                    .build();

            return List.of(hiddenDep);
        }
        return List.of();
    }
}