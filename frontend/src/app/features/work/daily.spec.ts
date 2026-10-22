import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Daily } from '../../core/models/daily';
import { PortfolioApiService } from '../../core/services/portfolio-api.service';
import { DailyBlock } from './daily';

const DAILY: Daily = {
  date: '2026-10-22',
  doneSince: '2026-10-21T05:00:00Z',
  done: [
    {
      projectId: 'distriapp',
      projectName: 'DistriApp',
      tasks: [{ scope: 'mvp-e2e-sin-mocks', number: '4.8', text: 'Desplegar sci360' }],
      commits: [{ sha: '53e5562', subject: 'docs(openspec): propuesta usuarios-desde-subscription-hub' }],
      moreCommits: 2,
    },
  ],
  focus: {
    projectId: 'nolost', projectName: 'NoLost', repo: 'nolost', kind: 'CHANGE', changeName: 'kids',
    nextNumber: '9.4', nextText: 'Carga real', done: 33, total: 34, inObjective: true,
  },
  alternatives: [],
  blockers: [
    {
      projectId: 'subscription-hub', projectName: 'Subscription Hub', kind: 'EDITORIAL', type: 'PRODUCT_DECISION',
      description: 'Elegir proveedor de pagos', owner: 'Daniel', scope: null, number: null, taskText: null,
    },
    {
      projectId: 'distriapp', projectName: 'DistriApp', kind: 'TASK', type: null,
      description: 'en prod no hay asesores', owner: null, scope: 'mvp-e2e-sin-mocks', number: '4.9',
      taskText: 'Verificar el ciclo E2E completo en producción',
    },
  ],
  commands: ['cd nolost', 'git status', 'git pull', 'claude', '/opsx:apply kids'],
  continuation: 'Proyecto: NoLost',
  stale: false,
  text: 'Daily — 2026-10-22',
};

function render(daily: Daily): HTMLElement {
  TestBed.configureTestingModule({
    imports: [DailyBlock],
    providers: [provideRouter([]), { provide: PortfolioApiService, useValue: { daily: () => of(daily) } }],
  });
  const fixture = TestBed.createComponent(DailyBlock);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('DailyBlock', () => {
  it('shows done, today and blockers', () => {
    const text = render(DAILY).textContent ?? '';

    expect(text).toContain('desde el 2026-10-21');
    expect(text).toContain('Desplegar sci360');
    expect(text).toContain('53e5562');
    expect(text).toContain('… y 2 commits más');
    expect(text).toContain('Carga real');
    expect(text).toContain('Elegir proveedor de pagos');
    expect(text).toContain('Responsable: Daniel');
    expect(text).toContain('tarea bloqueada');
    expect(text).toContain('en prod no hay asesores');
    expect(text.indexOf('Hecho')).toBeLessThan(text.indexOf('Para hoy trabajaré en'));
    expect(text.indexOf('Para hoy trabajaré en')).toBeLessThan(text.indexOf('Bloqueos'));
  });

  it('links a blocked OpenSpec task to its change', () => {
    const links = Array.from(render(DAILY).querySelectorAll('a')).map(a => a.getAttribute('href'));

    expect(links).toContain('/projects/distriapp/changes/mvp-e2e-sin-mocks');
    expect(links).toContain('/projects/subscription-hub?tab=blockers');
  });

  it('says so when there is nothing done or blocked', () => {
    const text = render({ ...DAILY, done: [], blockers: [] }).textContent ?? '';

    expect(text).toContain('Sin actividad registrada.');
    expect(text).toContain('Nada bloqueado.');
  });
});
