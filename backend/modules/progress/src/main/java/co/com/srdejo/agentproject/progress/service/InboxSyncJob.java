package co.com.srdejo.agentproject.progress.service;

import co.com.srdejo.agentproject.parser.api.SyncPayload;
import co.com.srdejo.agentproject.parser.api.SyncValidationException;
import co.com.srdejo.agentproject.parser.service.SyncPayloadParser;
import co.com.srdejo.agentproject.projects.api.ProjectSyncPort;
import co.com.srdejo.agentproject.projects.api.ProjectSyncRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Polls the sync inbox for JSON files dropped by OpenClaw (see docs/SYNC_PROTOCOL.md) and applies
 * them onto {@code modules:projects} through {@link ProjectSyncPort}. Never invents progress: a file
 * that fails validation goes to {@code rejected/} untouched, the project state is not modified.
 */
@Component
public class InboxSyncJob {

    private static final Logger log = LoggerFactory.getLogger(InboxSyncJob.class);

    private final SyncPayloadParser parser;
    private final ProjectSyncPort syncPort;
    private final Path inboxDir;

    public InboxSyncJob(SyncPayloadParser parser, ProjectSyncPort syncPort,
                         @Value("${agent-project.sync.inbox-dir}") String inboxDir) {
        this.parser = parser;
        this.syncPort = syncPort;
        this.inboxDir = Path.of(inboxDir);
    }

    @Scheduled(fixedDelayString = "${agent-project.sync.poll-interval-ms}")
    public void poll() {
        List<Path> files = listInboxFiles();
        for (Path file : files) {
            processFile(file);
        }
    }

    private List<Path> listInboxFiles() {
        try {
            Files.createDirectories(inboxDir);
            try (Stream<Path> stream = Files.list(inboxDir)) {
                return stream.filter(p -> p.toString().endsWith(".json")).toList();
            }
        } catch (IOException e) {
            log.error("Failed to list sync inbox directory {}", inboxDir, e);
            return List.of();
        }
    }

    private void processFile(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read inbox file {}", file, e);
            return;
        }

        SyncPayload payload;
        try {
            payload = parser.parse(content);
        } catch (SyncValidationException e) {
            log.warn("Rejected sync file {}: {}", file.getFileName(), e.getMessage());
            moveTo(file, "rejected");
            return;
        }

        String hash = sha256(content.trim());
        var request = new ProjectSyncRequest(
                payload.id(),
                payload.name(),
                payload.repo(),
                payload.stage(),
                payload.status(),
                payload.progress(),
                payload.updated(),
                payload.commit(),
                payload.verify(),
                payload.completed(),
                payload.next(),
                payload.blocked(),
                payload.checks().stream()
                        .map(c -> new ProjectSyncRequest.Check(c.name(), c.ok(), c.duration()))
                        .toList(),
                payload.events().stream()
                        .map(e -> new ProjectSyncRequest.Event(e.time(), e.mark(), e.text()))
                        .toList(),
                hash
        );

        var outcome = syncPort.applySync(request);
        log.info("Synced project {} from {}: {}", payload.id(), file.getFileName(), outcome);
        moveTo(file, "processed", payload.id());
    }

    private void moveTo(Path file, String subdir) {
        moveTo(file, subdir, null);
    }

    private void moveTo(Path file, String subdir, String projectId) {
        try {
            Path targetDir = inboxDir.resolve(subdir);
            Files.createDirectories(targetDir);
            String prefix = projectId != null ? projectId + "-" : "";
            Path target = targetDir.resolve(prefix + Instant.now().toEpochMilli() + "-" + file.getFileName());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to move processed inbox file {} to {}", file, subdir, e);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
