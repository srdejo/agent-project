import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'agent',
    loadComponent: () => import('./features/agent/agent-status').then((m) => m.AgentStatus),
  },
  {
    // El listado ordenable original. Dejo de ser la vista raiz cuando el mapa
    // paso a serlo, pero se conserva entera: orden por columnas, feed de
    // actividad y modal de tareas bloqueadas.
    path: 'dashboard',
    loadComponent: () => import('./features/projects/project-list').then((m) => m.ProjectList),
  },
  {
    // El mapa del portafolio: agrupa por prioridad, no por numero. Responde
    // "en que avanzo y que congelo", que es lo que el listado no contesta.
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/portfolio/portfolio-map').then((m) => m.PortfolioMap),
  },
  {
    // Comodin: tiene que ir ultimo, si no se traga /dashboard y /agent.
    path: ':id',
    loadComponent: () => import('./features/projects/project-detail').then((m) => m.ProjectDetail),
  },
];
