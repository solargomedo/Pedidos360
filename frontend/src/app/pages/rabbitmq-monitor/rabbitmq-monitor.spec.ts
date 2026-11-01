import { Component } from '@angular/core';
import { MessageActivity } from './message-activity';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { vi, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RabbitmqMonitor } from './rabbitmq-monitor';
import { Monitor } from '../../models/rabbitmq-monitor';

@Component({selector:'app-message-activity',standalone:true,template:''})
class ActivityStub { refresh() {} }

const url = 'https://mtdgm9u772.execute-api.us-east-1.amazonaws.com/api/bff/rabbitmq/monitor';
const partial: Monitor = {
  queriedAt: '2026-11-01T20:30:00Z', status: 'partial',
  nodes: { resource: 'nodes', status: 'available', error: null, data: [
    { name: 'rabbit@real-node', running: true, mem_alarm: false, disk_free_alarm: null, partitions: [] }
  ] },
  queues: [{resource:'pedidos.notificaciones', status:'available', error:null,
    data:{name:'pedidos.notificaciones',messages_ready:0,messages_unacknowledged:null,messages:null,consumers:1}}],
  exchanges: [], bindings: null
};

describe('RabbitmqMonitor', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<RabbitmqMonitor>>;
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    TestBed.configureTestingModule({
      imports: [RabbitmqMonitor], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    });
    TestBed.overrideComponent(RabbitmqMonitor, { remove: { imports: [MessageActivity] }, add: { imports: [ActivityStub] } });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RabbitmqMonitor);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
  });
  afterEach(() => {
    fixture.destroy(); http.verify(); vi.useRealTimers();
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  });
  it('renders partial real responses, zero and unknown metrics distinctly', () => {
    http.expectOne(url).flush(partial);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('rabbit@real-node');
    expect(text).toContain('Informaci?n parcial');
    const cells = fixture.nativeElement.querySelectorAll('tbody tr')[0].querySelectorAll('td');
    expect(cells[2].textContent.trim()).toBe('0');
    expect(cells[3].textContent.trim()).toBe('?');
    expect(text).toContain('Diagrama explicativo est?tico');
  });
  it('does not overlap requests and cancels on destroy', () => {
    const request = http.expectOne(url);
    vi.advanceTimersByTime(15000);
    http.expectNone(url);
    fixture.destroy();
    expect(request.cancelled).toBe(true);
  });
  it('polls every five seconds after success', () => {
    http.expectOne(url).flush(partial);
    vi.advanceTimersByTime(4999); http.expectNone(url);
    vi.advanceTimersByTime(1); http.expectOne(url).flush(partial);
  });
  it('pauses while hidden and refreshes when visible', () => {
    http.expectOne(url).flush(partial);
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(10000); http.expectNone(url);
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    document.dispatchEvent(new Event('visibilitychange'));
    http.expectOne(url).flush(partial);
  });
  it('backs off on network errors and permits manual refresh', () => {
    http.expectOne(url).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se pudo actualizar');
    expect(fixture.nativeElement.textContent).toContain('No hay datos disponibles');
    vi.advanceTimersByTime(5000); http.expectNone(url);
    fixture.nativeElement.querySelector('button').click();
    http.expectOne(url).flush(partial);
  });
  it('shows structured 503 without replacing absent metrics with zeros', () => {
    http.expectOne(url).flush({queriedAt:partial.queriedAt,status:'unavailable',nodes:null,queues:null,exchanges:null,bindings:null},
      {status:503,statusText:'Service Unavailable'});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('unavailable');
    expect(fixture.nativeElement.textContent).toContain('Sin informaci?n de colas');
  });
  it('stops automatic requests after forbidden response', () => {
    http.expectOne(url).flush({}, {status:403,statusText:'Forbidden'});
    vi.advanceTimersByTime(120000); http.expectNone(url);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('se requiere rol Admin');
  });
  it('retains previous data with a stale warning on network failure', () => {
    http.expectOne(url).flush(partial);
    vi.advanceTimersByTime(5000);
    http.expectOne(url).error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('rabbit@real-node');
    expect(fixture.nativeElement.textContent).toContain('Datos sin actualizar');
  });
});
