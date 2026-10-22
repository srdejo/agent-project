package co.com.srdejo.agentproject.domain.model;

import java.time.Instant;

/**
 * A task that went from open to {@code [x]} between two syncs. {@code scope} is the change name for an OpenSpec task
 * and the stage key (or title) for a ROADMAP task; {@code closedAt} is the {@code generated_at} of the snapshot.
 */
public record ClosedTask(Long id, String projectId, Task.TaskSource source, String scope, String number, String text,
                         Instant closedAt) {
}
