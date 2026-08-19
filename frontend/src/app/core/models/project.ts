export type ProjectStatus = 'IN_PROGRESS' | 'BLOCKED' | 'STARTED' | 'COMPLETED';
export type VerificationStatus = 'PASSED' | 'ATTENTION' | 'PENDING';
export type TaskStatus = 'done' | 'wip' | 'blocked' | 'todo';

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
  stack: string[];
  tasks: ProjectTask[];
  checks: VerificationCheck[];
  events: AgentEvent[];
  history: ProjectSnapshot[];
}
