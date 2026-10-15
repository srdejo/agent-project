package co.com.srdejo.agentproject.infrastructure.input.inbox;

import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Reacts to {@code snapshot.json} dropped in the inbox by the portfolio generator (docs/SYNC_PROTOCOL.md).
 * {@link WatchService} with a short debounce, so a file still being renamed into place is read once; file I/O runs
 * on {@code boundedElastic}. Every processing leaves one sync run, and the file is always deleted. Pre-v2 files
 * ({@code progreso.json}, {@code nuevo.json}) are recorded as obsolete and deleted unapplied.
 */
@Slf4j
@Component
public class InboxSyncJob {

    static final String SNAPSHOT_FILE = "snapshot.json";
    static final List<String> OBSOLETE_FILES = List.of("progreso.json", "nuevo.json");
    private static final long DEBOUNCE_MILLIS = 400L;

    private final SnapshotParser parser;
    private final ISyncServicePort syncService;
    private final Clock clock;
    private final Path inboxDir;

    private final ExecutorService watchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService debounceExecutor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("inbox-sync-debounce").factory());
    private final Object debounceLock = new Object();
    private WatchService watchService;
    private volatile boolean running;
    private ScheduledFuture<?> pendingFlush;

    public InboxSyncJob(SnapshotParser parser, ISyncServicePort syncService, Clock clock,
                        @Value("${agent-project.sync.inbox-dir}") String inboxDir) {
        this.parser = parser;
        this.syncService = syncService;
        this.clock = clock;
        this.inboxDir = Path.of(inboxDir);
    }

    @PostConstruct
    public void start() {
        running = true;
        watchExecutor.submit(this::watchLoop);
        // A file dropped while the service was down would otherwise wait for the next event.
        scheduleFlush();
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

    /** Processes whatever is in the inbox right now; emits one run per file found. */
    public Flux<SyncRun> flush() {
        return Flux.fromIterable(OBSOLETE_FILES)
                .filter(name -> Files.isRegularFile(inboxDir.resolve(name)))
                .concatMap(name -> {
                    log.warn("Ignoring {}: pre-v2 sync format, the generator must send {}", name, SNAPSHOT_FILE);
                    return syncService.recordFileError("formato obsoleto: " + name, clock.instant())
                            .doOnTerminate(() -> deleteQuietly(inboxDir.resolve(name)));
                })
                .concatWith(Mono.fromCallable(() -> Files.isRegularFile(inboxDir.resolve(SNAPSHOT_FILE)))
                        .filter(Boolean::booleanValue)
                        .flatMap(present -> processSnapshot(inboxDir.resolve(SNAPSHOT_FILE))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<SyncRun> processSnapshot(Path file) {
        Instant startedAt = clock.instant();
        return Mono.fromCallable(() -> {
                    try {
                        return parser.parse(Files.readString(file, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(batch -> syncService.applySnapshot(batch, startedAt))
                .doOnNext(run -> log.info("Sync run: {} created, {} updated, {} unchanged, {} rejected, {} unreadable, "
                                + "{} newly missing", run.created().size(), run.updated().size(), run.unchanged().size(),
                        run.rejected().size(), run.readErrors().size(), run.missing().size()))
                .onErrorResume(SnapshotValidationException.class, e -> {
                    log.warn("Rejected {}: {}", SNAPSHOT_FILE, e.getMessage());
                    return syncService.recordFileError(e.getMessage(), startedAt);
                })
                .onErrorResume(UncheckedIOException.class, e -> {
                    log.error("Failed to read {}", file, e);
                    return syncService.recordFileError("no se pudo leer el archivo: " + e.getCause().getMessage(),
                            startedAt);
                })
                .doOnTerminate(() -> deleteQuietly(file));
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
                Files.createDirectories(inboxDir);
                watchService = FileSystems.getDefault().newWatchService();
                return inboxDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            } catch (IOException e) {
                log.error("Failed to register watch on inbox directory {}, retrying in {} ms", inboxDir, DEBOUNCE_MILLIS, e);
                sleepQuietly();
            }
        }
        return null;
    }

    private void pollEvents(WatchKey initialKey) throws InterruptedException {
        WatchKey key = initialKey;
        while (running) {
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.context() instanceof Path path) {
                    String fileName = path.getFileName().toString();
                    if (fileName.equals(SNAPSHOT_FILE) || OBSOLETE_FILES.contains(fileName)) {
                        scheduleFlush();
                    }
                }
            }
            if (!key.reset()) {
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
            pendingFlush = debounceExecutor.schedule(
                    () -> flush().doOnError(e -> log.error("Inbox sync failed", e)).onErrorComplete().blockLast(),
                    DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(DEBOUNCE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete inbox file {}", file, e);
        }
    }
}
