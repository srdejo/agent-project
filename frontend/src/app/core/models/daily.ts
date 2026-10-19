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

export interface DailyNeed {
  projectId: string;
  projectName: string;
  type: string;
  description: string;
  owner: string | null;
}

export interface Daily {
  date: string;
  /** `null` when nothing is actionable. */
  focus: DailyItem | null;
  alternatives: DailyItem[];
  needs: DailyNeed[];
  commands: string[];
  continuation: string | null;
  stale: boolean;
  /** The whole daily as plain text, ready to copy. */
  text: string;
}
