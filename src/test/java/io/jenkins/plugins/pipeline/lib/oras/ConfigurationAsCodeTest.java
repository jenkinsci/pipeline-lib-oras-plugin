package io.jenkins.plugins.pipeline.lib.oras;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.util.List;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void testConfigurationAsCode(JenkinsConfiguredWithCodeRule jenkinsRule) {
        GlobalLibraries globalLibraries = GlobalLibraries.get();
        List<LibraryConfiguration> libraries = globalLibraries.getLibraries();
        assertEquals(1, libraries.size());
        LibraryConfiguration testLibrary = libraries.get(0);
        LibraryRetriever retriever = testLibrary.getRetriever();
        OrasRetriever orasRetriever = (OrasRetriever) retriever;
        assertEquals("test-library", testLibrary.getName());
        assertEquals("localhost:8080/test-library", orasRetriever.getContainerRef());
    }
}
