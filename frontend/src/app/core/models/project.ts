/** Shapes returned by the backend (docs/ARCHITECTURE.md, "Endpoints"). Dates are ISO-8601 strings. */

export type ProjectState = 'ACTIVE' | 'NEXT' | 'PAUSED' | 'BLOCKED' | 'MAINTENANCE' | 'COMPLETED' | 'ARCHIVED';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE' | 'CANCELLED';
export type BlockerType =
  | 'PRODUCT_DECISION'
  | 'EXTERNAL_DEPENDENCY'
  | 'TECHNICAL'
  | 'ENVIRONMENT'
  | 'SECURITY'
  | 'UNKNOWN';

/** `percent` is null when the scope has no countable tasks: show "Sin datos", never 0%. */
export interface ObjectiveSummary {
  name: string;
  done: number;
  total: number;
  percent: number | null;
  achieved: boolean;
}

export interface Counts {
  done: number;
  total: number;
  percent: number | null;
}

/** `openTasksAffected` is null when none of `affects` resolved: show "sin datos". */
export interface Blocker {
  id: string;
  type: BlockerType;
  description: string;
  impact: string | null;
  affects: string[];
  owner: string | null;
  created: string | null;
  resolution: string | null;
  openTasksAffected: number | null;
}

/** `objective` is null when the ROADMAP declares none ("Sin objetivo declarado"). */
export interface ProjectCard {
  id: string;
  name: string;
  alias: string | null;
  summary: string | null;
  state: ProjectState;
  priorityRank: number | null;
  stack: string[];
  objective: ObjectiveSummary | null;
  roadmap: Counts | null;
  activeChanges: number;
  openspecDetected: boolean;
  blockerCount: number;
  mainBlocker: Blocker | null;
  lastActivity: string | null;
  missingSince: string | null;
  readError: string | null;
  readErrorAt: string | null;
  hasActionable: boolean;
}

export interface Task {
  id: number;
  source: 'ROADMAP' | 'OPENSPEC';
  origin: string | null;
  number: string | null;
  text: string;
  section: string | null;
  line: number;
  status: TaskStatus;
  blockedReason: string | null;
  inObjective: boolean;
  heldByBlocker: string | null;
}

export interface ScopeRef {
  ref: string;
  kind: 'STAGE' | 'CHANGE';
  resolved: boolean;
}

export interface TaskGroup {
  kind: 'STAGE' | 'CHANGE';
  label: string;
  done: number;
  total: number;
  tasks: Task[];
}

export interface ObjectiveDetail {
  name: string;
  criteria: string | null;
  targetDate: string | null;
  scope: ScopeRef[];
  done: number;
  total: number;
  percent: number | null;
  achieved: boolean;
  groups: TaskGroup[];
}

export interface Stage {
  key: string | null;
  title: string;
  marker: 'closed' | 'in_progress' | 'none';
  hasCriteria: boolean;
  backlog: boolean;
  done: number;
  total: number;
  tasks: Task[];
}

export interface AchievedObjective {
  date: string | null;
  name: string;
  criteria: string | null;
}

export interface HistoryPoint {
  takenAt: string;
  done: number;
  total: number;
}

/** `evidence` stays NOT_AVAILABLE until independent verification evidence exists (Fase 2). */
export interface ProjectDetail {
  card: ProjectCard;
  sourceCommit: string | null;
  objective: ObjectiveDetail | null;
  achievedObjectives: AchievedObjective[];
  stages: Stage[];
  blockers: Blocker[];
  history: HistoryPoint[];
  evidence: string;
}
