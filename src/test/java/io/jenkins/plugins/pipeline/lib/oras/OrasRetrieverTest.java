package io.jenkins.plugins.pipeline.lib.oras;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import land.oras.ContainerRef;
import land.oras.LocalPath;
import land.oras.Registry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wiremock.org.apache.commons.io.IOUtils;

@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
class OrasRetrieverTest {

    @Rule
    public LoggerRule logger = new LoggerRule().record("land.oras", Level.ALL);

    @Container
    private final ZotContainer container = new ZotContainer().withStartupAttempts(3);

    @BeforeEach
    void before() {
        Registry registry = Registry.builder()
                .insecure(this.container.getRegistry(), "myuser", "mypass")
                .build();
        ContainerRef containerRef = ContainerRef.parse("%s/lib:latest".formatted(this.container.getRegistry()));
        System.out.println(TarArchiveInputStream.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation());
        registry.pushArtifact(
                containerRef,
                OrasRetriever.ARTIFACT_TYPE,
                LocalPath.of(Path.of("src/test/resources/io/jenkins/plugins/pipeline/lib/oras")
                        .toAbsolutePath()));
    }

    @Test
    void shouldRunPipeline(JenkinsRule jenkinsRule) throws Exception {

        String pipelineContent = IOUtils.toString(
                Objects.requireNonNull(getClass().getResourceAsStream("Jenkinsfile")), StandardCharsets.UTF_8);

        // Create job
        WorkflowJob p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(pipelineContent, true));

        // Create the library configuration
        OrasRetriever retriever = new OrasRetriever("%s/lib".formatted(this.container.getRegistry()));
        LibraryConfiguration lib = new LibraryConfiguration("oras", retriever);
        lib.setImplicit(true);
        lib.setDefaultVersion("latest");

        GlobalLibraries globalLibraries = GlobalLibraries.get();
        globalLibraries.setLibraries(List.of(lib));

        // Run
        WorkflowRun b = jenkinsRule.buildAndAssertSuccess(p);

        // Assert the library is loaded
        jenkinsRule.assertLogContains("This is myFunc from the oras library", b);
    }
}
