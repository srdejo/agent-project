export interface DailyItem {
  projectId: string;
  projectName: string;
  repo: string;
  kind: 'CHANGE' | 'TASK';
  changeName: string | null;
  nextNumber: string | null;
  nextText: string;
  /** Only for changes in progress. */
  done: number | null;
  total: number | null;
  inObjective: boolean;
}

export interface DailyDoneTask {
  /** The change name, or the stage key or title of a ROADMAP task. */
  scope: string;
  number: string | null;
  text: string;
}

export interface DailyDoneCommit {
  sha: string;
  subject: string;
}

export interface DailyDoneGroup {
  projectId: string;
  projectName: string;
  tasks: DailyDoneTask[];
  commits: DailyDoneCommit[];
  /** Commits of the period left out of `commits`. */
  moreCommits: number;
}

/**
 * EDITORIAL comes from portfolio.json (`type`, `owner`); TASK is a task the repo declares blocked, and then
 * `description` is the reason and `scope`/`number`/`taskText` locate the task.
 */
export interface DailyBlocker {
  projectId: string;
  projectName: string;
  kind: 'EDITORIAL' | 'TASK';
  type: string | null;
  description: string;
  owner: string | null;
  scope: string | null;
  number: string | null;
  taskText: string | null;
}

export interface Daily {
  date: string;
  /** Start of the "Hecho" period: 00:00 of the last day before today with activity. */
  doneSince: string;
  done: DailyDoneGroup[];
  /** `null` when nothing is actionable. */
  focus: DailyItem | null;
  alternatives: DailyItem[];
  blockers: DailyBlocker[];
  commands: string[];
  continuation: string | null;
  stale: boolean;
  /** The whole daily as plain text, ready to copy. */
  text: string;
}
