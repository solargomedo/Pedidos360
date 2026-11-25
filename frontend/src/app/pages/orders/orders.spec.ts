import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Orders } from './orders';
import { ordersUrl } from './orders-url';

describe('Analítica de pedidos', () => {
  const analiticaUrl = `${ordersUrl}/analitica`;
  const indicadores = {
    totalPedidosProcesados: 6,
    totalUnidadesSolicitadas: 9,
    pedidosPorEstado: { CREADO: 6 }
  };

  let http: HttpTestingController;
  let role: string;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    role = 'Operador';
    TestBed.configureTestingModule({
      imports: [Orders],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MsalService,
          useValue: {
            instance: { getActiveAccount: () => ({}) },
            acquireTokenSilent: () => of({
              accessToken: `header.${btoa(JSON.stringify({ roles: [role] }))}.signature`
            })
          }
        }
      ]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('consulta cada cinco segundos, conserva el último dato y se recupera', () => {
    const fixture = TestBed.createComponent(Orders);
    fixture.detectChanges();
    http.expectOne(ordersUrl).flush([]);
    vi.advanceTimersByTime(0);
    http.expectOne(analiticaUrl).flush(indicadores);
    fixture.detectChanges();

    const section: HTMLElement = fixture.nativeElement.querySelector('[aria-labelledby="analitica-kafka"]');
    expect(section.textContent).toContain('Analítica de eventos Kafka');
    expect(section.textContent).toContain('6 pedidos procesados');
    expect(section.textContent).toContain('9 unidades solicitadas');
    expect(section.querySelectorAll('tbody tr')).toHaveLength(6);

    vi.advanceTimersByTime(4999);
    http.expectNone(analiticaUrl);
    vi.advanceTimersByTime(1);
    http.expectOne(analiticaUrl).flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(section.textContent).toContain('últimos datos disponibles');
    expect(section.textContent).toContain('6 pedidos procesados');

    vi.advanceTimersByTime(5000);
    http.expectOne(analiticaUrl).flush({ ...indicadores, totalPedidosProcesados: 7 });
    fixture.detectChanges();
    expect(section.textContent).toContain('7 pedidos procesados');
    expect(section.textContent).not.toContain('últimos datos disponibles');
    fixture.destroy();
  });

  it('omite ticks solapados y cancela la solicitud al destruirse', () => {
    const fixture = TestBed.createComponent(Orders);
    fixture.detectChanges();
    http.expectOne(ordersUrl).flush([]);
    vi.advanceTimersByTime(0);
    const pendiente = http.expectOne(analiticaUrl);
    vi.advanceTimersByTime(10000);
    http.expectNone(analiticaUrl);
    fixture.destroy();
    expect(pendiente.cancelled).toBe(true);
    vi.advanceTimersByTime(5000);
    http.expectNone(analiticaUrl);
  });

  it('no consulta analítica para Cliente', () => {
    role = 'Cliente';
    const fixture = TestBed.createComponent(Orders);
    fixture.detectChanges();
    vi.advanceTimersByTime(5000);
    http.expectNone(analiticaUrl);
    expect(fixture.nativeElement.querySelector('#analitica-kafka')).toBeNull();
    fixture.destroy();
  });
});
