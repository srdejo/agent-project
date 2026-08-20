package co.com.srdejo.agentproject.progress.service;

import co.com.srdejo.agentproject.parser.api.BatchParseResult;
import co.com.srdejo.agentproject.parser.api.SyncPayload;
import co.com.srdejo.agentproject.parser.api.SyncValidationException;
import co.com.srdejo.agentproject.parser.service.SyncPayloadParser;
import co.com.srdejo.agentproject.projects.api.ProjectSyncPort;
import co.com.srdejo.agentproject.projects.api.ProjectSyncRequest;
import co.com.srdejo.agentproject.projects.api.SyncOutcome;
import co.com.srdejo.agentproject.projects.api.SyncRunPort;
import co.com.srdejo.agentproject.projects.api.SyncRunRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Reacts to files dropped in the sync inbox by OpenClaw on each of its own runs (see
 * docs/SYNC_PROTOCOL.md): {@code progreso.json} (updates for projects that already exist) and
 * {@code nuevo.json} (brand new projects to create). Detection is event-driven via
 * {@link WatchService} on {@code inboxDir} — not polling: a virtual thread blocks on
 * {@link WatchService#take()} and reacts as soon as a file is created or modified. Since both
 * files can land within milliseconds of each other on the same OpenClaw run, events are
 * coalesced with a short debounce window before processing, so a single run produces a single
 * {@link SyncRunPort#record}. Each entry is validated and applied independently — one bad entry
 * never blocks the rest of the file. Both files are always deleted after being processed, since
 * OpenClaw regenerates them fresh on its next run; the real history lives in
 * {@code project_snapshots}, not on the filesystem.
 */
@Component
public class InboxSyncJob {

    private static final Logger log = LoggerFactory.getLogger(InboxSyncJob.class);
    private static final String PROGRESO_FILE = "progreso.json";
    private static final String NUEVO_FILE = "nuevo.json";
    private static final long DEBOUNCE_MILLIS = 400L;

    private final SyncPayloadParser parser;
    private final ProjectSyncPort syncPort;
    private final SyncRunPort syncRunPort;
    private final Path inboxDir;

    private final ExecutorService watchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService debounceExecutor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("inbox-sync-debounce").factory());

    private final Object debounceLock = new Object();
    private WatchService watchService;
    private volatile boolean running;
    private ScheduledFuture<?> pendingFlush;

    public InboxSyncJob(SyncPayloadParser parser, ProjectSyncPort syncPort, SyncRunPort syncRunPort,
                         @Value("${agent-project.sync.inbox-dir}") String inboxDir) {
        this.parser = parser;
        this.syncPort = syncPort;
        this.syncRunPort = syncRunPort;
        this.inboxDir = Path.of(inboxDir);
    }

    @PostConstruct
    public void start() {
        running = true;
        watchExecutor.submit(this::watchLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        synchronized (debounceLock) {
            if (pendingFlush != null) {
                pendingFlush.cancel(false);
            }
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Failed to close inbox watch service", e);
            }
        }
        debounceExecutor.shutdownNow();
        watchExecutor.shutdownNow();
    }

    private void watchLoop() {
        while (running) {
            WatchKey key = registerWatch();
            if (key == null) {
                return;
            }
            try {
                pollEvents(key);
            } catch (ClosedWatchServiceException e) {
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private WatchKey registerWatch() {
        while (running) {
            try {
                watchService = FileSystems.getDefault().newWatchService();
                return inboxDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            } catch (IOException e) {
                log.error("Failed to register watch on inbox directory {}, retrying in {} ms", inboxDir, DEBOUNCE_MILLIS, e);
                sleepQuietly(DEBOUNCE_MILLIS);
            }
        }
        return null;
    }

    private void pollEvents(WatchKey initialKey) throws InterruptedException {
        WatchKey key = initialKey;
        while (running) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Object context = event.context();
                if (context instanceof Path path) {
                    String fileName = path.getFileName().toString();
                    if (fileName.equals(PROGRESO_FILE) || fileName.equals(NUEVO_FILE)) {
                        scheduleFlush();
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warn("Inbox watch key became invalid for {}, re-registering", inboxDir);
                return;
            }

            key = watchService.take();
        }
    }

    private void scheduleFlush() {
        synchronized (debounceLock) {
            if (pendingFlush != null) {
                pendingFlush.cancel(false);
            }
            pendingFlush = debounceExecutor.schedule(this::flush, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void flush() {
        RunAccumulator accumulator = new RunAccumulator();
        boolean progresoProcessed = processIfPresent(PROGRESO_FILE, this::applyProgresoEntry, accumulator);
        boolean nuevoProcessed = processIfPresent(NUEVO_FILE, this::applyNuevoEntry, accumulator);

        if (progresoProcessed || nuevoProcessed) {
            syncRunPort.record(accumulator.toRecord());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean processIfPresent(String fileName, EntryHandler handler, RunAccumulator accumulator) {
        Path file = inboxDir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return false;
        }

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read inbox file {}", file, e);
            return false;
        }

        BatchParseResult result;
        try {
            result = parser.parseBatch(content);
        } catch (SyncValidationException e) {
            log.warn("Rejected {}: {}", fileName, e.getMessage());
            deleteQuietly(file);
            return true;
        }

        result.rejected().forEach((id, reason) -> {
            log.warn("Rejected entry '{}' in {}: {}", id, fileName, reason);
            accumulator.rejected.put(id, reason);
        });
        result.valid().forEach((id, payload) -> handler.apply(id, payload, accumulator));

        deleteQuietly(file);
        return true;
    }

    private void applyProgresoEntry(String id, SyncPayload payload, RunAccumulator accumulator) {
        if (!syncPort.exists(id)) {
            log.warn("Skipping '{}' in {}: project does not exist yet, use {} to create it", id, PROGRESO_FILE, NUEVO_FILE);
            accumulator.rejected.put(id, "project does not exist yet");
            return;
        }
        var outcome = syncPort.applySync(toRequest(id, payload));
        log.info("Synced project '{}' from {}: {}", id, PROGRESO_FILE, outcome);
        accumulator.record(id, outcome);
    }

    private void applyNuevoEntry(String id, SyncPayload payload, RunAccumulator accumulator) {
        if (syncPort.exists(id)) {
            log.warn("Skipping '{}' in {}: project already exists, use {} to update it", id, NUEVO_FILE, PROGRESO_FILE);
            accumulator.rejected.put(id, "project already exists");
            return;
        }
        var outcome = syncPort.applySync(toRequest(id, payload));
        log.info("Created project '{}' from {}: {}", id, NUEVO_FILE, outcome);
        accumulator.record(id, outcome);
    }

    private ProjectSyncRequest toRequest(String id, SyncPayload payload) {
        return new ProjectSyncRequest(
                id,
                payload.name(),
                payload.repo(),
                payload.stage(),
                payload.status(),
                payload.progress(),
                payload.updated(),
                payload.commit(),
                payload.verify(),
                payload.summary(),
                payload.stack(),
                payload.tasks().stream()
                        .map(t -> new ProjectSyncRequest.Task(t.name(), t.stage(), t.status(), t.date(), t.commit()))
                        .toList(),
                payload.checks().stream()
                        .map(c -> new ProjectSyncRequest.Check(c.name(), c.ok(), c.duration()))
                        .toList(),
                payload.events().stream()
                        .map(e -> new ProjectSyncRequest.Event(e.time(), e.mark(), e.text()))
                        .toList(),
                payload.lastModified()
        );
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete inbox file {}", file, e);
        }
    }

    @FunctionalInterface
    private interface EntryHandler {
        void apply(String id, SyncPayload payload, RunAccumulator accumulator);
    }

    private static final class RunAccumulator {
        private final List<String> created = new ArrayList<>();
        private final List<String> updated = new ArrayList<>();
        private final List<String> unchanged = new ArrayList<>();
        private final Map<String, String> rejected = new LinkedHashMap<>();

        void record(String id, SyncOutcome outcome) {
            switch (outcome) {
                case CREATED -> created.add(id);
                case UPDATED -> updated.add(id);
                case UNCHANGED -> unchanged.add(id);
            }
        }

        SyncRunRecord toRecord() {
            return new SyncRunRecord(created, updated, unchanged, rejected, Instant.now());
        }
    }
}
