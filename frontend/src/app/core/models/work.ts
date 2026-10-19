export interface ContinueItem {
  projectId: string;
  projectName: string;
  changeName: string;
  done: number;
  total: number;
  nextNumber: string | null;
  nextText: string;
  inObjective: boolean;
}

export interface ActionableItem {
  taskId: number;
  source: 'ROADMAP' | 'OPENSPEC';
  origin: string | null;
  changeName: string | null;
  number: string | null;
  text: string;
  status: 'OPEN' | 'IN_PROGRESS';
  inObjective: boolean;
}

export interface ActionableGroup {
  projectId: string;
  projectName: string;
  items: ActionableItem[];
  /** How many more actionable tasks the project has beyond `items`. */
  more: number;
}

export interface DecisionItem {
  projectId: string;
  projectName: string;
  blockerId: string;
  description: string;
  owner: string | null;
  impact: string | null;
  affects: string[];
  openTasksAffected: number | null;
}

export type ReviewKind =
  | 'CHANGE_COMPLETE'
  | 'OBJECTIVE_ACHIEVED'
  | 'NO_OBJECTIVE'
  | 'READ_ERROR'
  | 'MISSING'
  | 'CHANGE_PARSE_ERROR';

export interface ReviewItem {
  kind: ReviewKind;
  projectId: string;
  projectName: string;
  changeName: string | null;
  message: string;
}

export interface ActivityItem {
  at: string;
  kind: 'SYNC' | 'PROGRESS';
  projectId: string | null;
  text: string;
}

export interface Work {
  continue: ContinueItem[];
  actionable: ActionableGroup[];
  decisions: DecisionItem[];
  review: ReviewItem[];
  activity: ActivityItem[];
}
