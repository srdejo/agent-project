export interface SyncHealth {
  lastAttemptedSync: string | null;
  lastSuccessfulSync: string | null;
  dataGeneratedAt: string | null;
  lastRun: {
    startedAt: string;
    durationMs: number;
    fileError: string | null;
    created: string[];
    updated: string[];
    unchanged: string[];
    missing: string[];
    rejected: Record<string, string>;
    readErrors: Record<string, string>;
  } | null;
  dataAgeSeconds: number | null;
  /** True when the data is older than the backend threshold, or there is no data at all. */
  stale: boolean;
  staleAfterSeconds: number;
  /** Comes only from the agent's heartbeat, never from "the API answered". */
  agentConnectivity: 'online' | 'offline';
}
