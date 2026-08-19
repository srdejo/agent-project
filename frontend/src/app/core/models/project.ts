export type ProjectStatus = 'IN_PROGRESS' | 'BLOCKED' | 'STARTED' | 'COMPLETED';
export type VerificationStatus = 'PASSED' | 'ATTENTION' | 'PENDING';

export interface VerificationCheck {
  name: string;
  duration: string;
  ok: boolean;
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
  completed: string[];
  next: string[];
  blocked: string[];
  checks: VerificationCheck[];
  events: AgentEvent[];
  history: ProjectSnapshot[];
}
