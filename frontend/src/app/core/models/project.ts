export type ProjectStatus = 'IN_PROGRESS' | 'BLOCKED' | 'STARTED' | 'COMPLETED';
export type VerificationStatus = 'PASSED' | 'ATTENTION' | 'PENDING';
export type TaskStatus = 'done' | 'wip' | 'blocked' | 'todo';

/**
 * Capa editorial del portafolio: la decide el usuario, no se deriva de nada.
 * Viaja en el JSON del inbox (`priority`) — ver docs/SYNC_PROTOCOL.md.
 * `null` significa que el proyecto todavía no se ha clasificado.
 */
export type ProjectPriority = 'NOW' | 'NEXT' | 'DECIDE' | 'ON_TRACK' | 'FROZEN';

export interface VerificationCheck {
  name: string;
  duration: string;
  ok: boolean;
}

export interface ProjectTask {
  name: string;
  stage: string;
  status: TaskStatus;
  date: string;
  commit: string;
}

export interface AgentEvent {
  time: string;
  mark: string;
  text: string;
}

export interface ProjectSnapshot {
  takenAt: string;
  progress: number;
}

export interface ProjectSummary {
  id: string;
  name: string;
  repo: string;
  progress: number;
  stage: string;
  status: ProjectStatus;
  updated: string;
  summary: string | null;
  alias: string | null;
  priority: ProjectPriority | null;
  /** Posición en el orden del portafolio, 1 = primero. Distinto de `priority`, que es el grupo. */
  priorityRank: number | null;
  openQuestion: string | null;
  series: number[];
  tasksDone: number;
  tasksTotal: number;
  events: AgentEvent[];
}

export interface ProjectStats {
  count: number;
  avg: number;
  blocked: number;
  verified: number;
}

export interface ProjectListResponse {
  projects: ProjectSummary[];
  stats: ProjectStats;
  lastSync: string | null;
}

export interface BlockedTask {
  projectId: string;
  projectName: string;
  taskName: string;
  stage: string;
  date: string;
}

export interface ProjectDetail {
  id: string;
  name: string;
  repo: string;
  progress: number;
  stage: string;
  status: ProjectStatus;
  updated: string;
  commit: string;
  verify: VerificationStatus;
  summary: string | null;
  alias: string | null;
  priority: ProjectPriority | null;
  /** Posición en el orden del portafolio, 1 = primero. Distinto de `priority`, que es el grupo. */
  priorityRank: number | null;
  openQuestion: string | null;
  stack: string[];
  tasks: ProjectTask[];
  checks: VerificationCheck[];
  events: AgentEvent[];
  history: ProjectSnapshot[];
}
