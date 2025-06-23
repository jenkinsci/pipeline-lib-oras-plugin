package io.jenkins.plugins.pipeline.lib.oras;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.security.ACL;
import hudson.slaves.WorkspaceList;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import jenkins.model.Jenkins;
import land.oras.ArtifactType;
import land.oras.ContainerRef;
import land.oras.Manifest;
import land.oras.Registry;
import land.oras.utils.Const;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.LibraryRetrieverDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Restricted(NoExternalUse.class)
public class OrasRetriever extends LibraryRetriever {

    /**
     * Required artifact type for ORAS retriever.
     */
    public static final ArtifactType ARTIFACT_TYPE = ArtifactType.from("application/vnd.jenkins.lib.manifest.v1+json");

    /**
     * Logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(OrasRetriever.class);

    /**
     * Credentials ID to retrieve the library artifact from the registry.
     */
    private String credentialsId;

    /**
     * Reference to the container in which the flow is defined such as my-registry/my-container:latest
     */
    private final String containerRef;

    @DataBoundConstructor
    public OrasRetriever(String containerRef) {
        this.containerRef = containerRef;
    }

    @Override
    public void retrieve(
            @NonNull String name,
            @NonNull String version,
            boolean changelog,
            @NonNull FilePath target,
            @NonNull Run<?, ?> run,
            @NonNull TaskListener listener)
            throws Exception {

        Item item = run.getParent();

        Registry registry = buildRegistry(item, credentialsId);
        Credentials currentCredentials = getCredentials(item, this.credentialsId);
        if (currentCredentials != null) {
            CredentialsProvider.track(item, currentCredentials);
        }

        // We should support digest also here
        ContainerRef libraryRef = ContainerRef.parse(this.containerRef + ":" + version);

        Manifest manifest = registry.getManifest(libraryRef);
        ensureArtifactType(manifest);

        String digest = manifest.getDigest();
        String revision = manifest.getAnnotations().getOrDefault(Const.ANNOTATION_REVISION, "unknown");
        String source = manifest.getAnnotations().getOrDefault(Const.ANNOTATION_SOURCE, "unknown");

        FilePath dir = getDownloadFolder(name, run);
        Computer computer = Jenkins.get().toComputer();
        if (computer == null) {
            throw new IOException(Jenkins.get().getDisplayName() + " may be offline");
        }

        try (WorkspaceList.Lease lease = computer.getWorkspaceList().allocate(dir)) {

            registry.pullArtifact(libraryRef, Path.of(lease.path.getRemote()), true);

            LOG.trace(
                    "Pulled library {} with version {} (digest: {}) (revision: {}) to {}",
                    this.containerRef,
                    version,
                    digest,
                    revision,
                    lease.path.getRemote());

            // Move vars, src and resources directories to the root of the library if needed
            if (lease.path.list().size() == 1 && lease.path.list().get(0).isDirectory()) {
                LOG.trace(
                        "Moving contents of {} to the root of the library",
                        lease.path.list().get(0).getName());
                String sharedLibUpperDir = lease.path.list().get(0).getName();
                if (!sharedLibUpperDir.equals("src")
                        && !sharedLibUpperDir.equals("vars")
                        && !sharedLibUpperDir.equals("resources")) {
                    lease.path.list().get(0).moveAllChildrenTo(lease.path);
                    LOG.trace(
                            "Moved contents of {} to the root of the library",
                            lease.path.list().get(0).getName());
                } else {
                    LOG.trace(
                            "No need to move contents of {} to the root of the library, as it is already in the correct location",
                            sharedLibUpperDir);
                }
            } else {
                LOG.trace(
                        "Library {} already has the correct structure, no need to move contents",
                        lease.path.getRemote());
            }

            lease.path.copyRecursiveTo(target);
            LOG.debug("Library copied to target directory: {}", target.getRemote());
        }

        listener.getLogger()
                .printf(
                        "Using library from %s@%s at revision %s and source %s%n",
                        this.containerRef, digest, revision, source);
    }

    @Override
    public void retrieve(
            @NonNull String name,
            @NonNull String version,
            @NonNull FilePath target,
            @NonNull Run<?, ?> run,
            @NonNull TaskListener listener)
            throws Exception {
        retrieve(name, version, false, target, run, listener); // No changelog support for ORAS retriever
    }

    @SuppressWarnings("unused")
    public String getCredentialsId() {
        return credentialsId;
    }

    @SuppressWarnings("unused")
    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getContainerRef() {
        return containerRef;
    }

    private FilePath getDownloadFolder(String name, Run<?, ?> run) throws IOException {
        FilePath dir;
        if (run.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = Jenkins.get().getWorkspaceFor((TopLevelItem) run.getParent());
            if (baseWorkspace == null) {
                throw new IOException(Jenkins.get().getDisplayName() + " may be offline");
            }
            dir = baseWorkspace.withSuffix(getFilePathSuffix() + "libs").child(name);
        } else {
            throw new AbortException("Cannot check out in non-top-level build");
        }
        return dir;
    }

    private static String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

    private static Registry buildRegistry(Item item, String credentialsId) {
        Registry.Builder builder = Registry.builder();
        if (credentialsId == null || credentialsId.isEmpty()) {
            return builder.insecure().build();
        }
        UsernamePasswordCredentials credentials = getCredentials(item, credentialsId);
        if (credentials == null) {
            throw new IllegalArgumentException("No credentials found with ID: " + credentialsId);
        }

        return builder.defaults(
                        credentials.getUsername(), credentials.getPassword().getPlainText())
                .build();
    }

    public static @Nullable StandardUsernamePasswordCredentials getCredentials(Item item, String credentialsId) {
        if (credentialsId == null || credentialsId.isEmpty()) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItem(
                        StandardUsernamePasswordCredentials.class, item, ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId),
                        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)));
    }

    private static void ensureArtifactType(Manifest manifest) {
        if (!Objects.equals(
                ARTIFACT_TYPE.getMediaType(), manifest.getArtifactType().getMediaType())) {
            throw new IllegalArgumentException(
                    "The container reference does not point to a valid pipeline manifest. Make sure to set application/vnd.jenkins.pipeline.manifest.v1+json artifact type when pushing the artifact");
        }
    }

    @Symbol("oras")
    @Extension
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends LibraryRetrieverDescriptor {

        @Override
        public @NonNull String getDisplayName() {
            return "Pipeline Library from ORAS";
        }

        @SuppressWarnings("unused")
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            final StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            StandardUsernameCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernameCredentials.class))
                    .includeCurrentValue(credentialsId);
        }
    }
}
