import { Task } from './project';

export interface ChangeSummary {
  name: string;
  archived: boolean;
  /** Present artifacts: proposal, design, tasks, metadata and each spec path. */
  artifacts: string[];
  done: number;
  total: number;
  inObjective: boolean;
  parseError: string | null;
  parseErrorAt: string | null;
  lastParsedAt: string | null;
}

export interface ChangeList {
  openspecDetected: boolean;
  specsCount: number;
  archivedCount: number;
  commands: string[];
  changes: ChangeSummary[];
}

export interface Artifact {
  content: string;
  truncated: boolean;
}

export interface SpecArtifact extends Artifact {
  path: string;
}

/** Artifacts are raw Markdown and must be rendered as text, never as HTML. A missing artifact is null. */
export interface ChangeDetail {
  projectId: string;
  projectName: string;
  repo: string;
  name: string;
  archived: boolean;
  path: string;
  metadata: string | null;
  artifacts: {
    proposal: Artifact | null;
    design: Artifact | null;
    tasks: Artifact | null;
    specs: SpecArtifact[];
  } | null;
  tasks: Task[];
  done: number;
  total: number;
  inObjective: boolean;
  parseError: string | null;
  parseErrorAt: string | null;
  lastParsedAt: string | null;
  commands: string[];
  evidence: string;
}

export interface Continuation {
  text: string;
  /** Null when the needed opsx command is not installed in the repo. */
  suggestedCommand: string | null;
  stale: boolean;
}
