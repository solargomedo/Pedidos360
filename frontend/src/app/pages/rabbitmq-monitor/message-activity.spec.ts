import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi, beforeEach, afterEach, it, describe, expect } from 'vitest';
import { MessageActivity } from './message-activity';
import { MessagingEventsPage, MessagingEvent } from '../../models/messaging-events';

const endpoint = 'https://mtdgm9u772.execute-api.us-east-1.amazonaws.com/api/bff/rabbitmq/events';
const event = (id: string, sequence: number): MessagingEvent => ({
  eventId: id, sequence, timestamp: '2026-11-01T20:30:00Z', pedidoId:42,
  exchange:'pedidos.direct',routingKey:'pedido.creado',queue:null,consumer:null,
  attempt:null,type:'PUBLICATION_REQUESTED',result:'REQUESTED'
});
const page = (events: MessagingEvent[], nextAfter: number, instanceId = 'instance-a'): MessagingEventsPage =>
  ({instanceId,capacity:1000,earliestSequence:1,lastSequence:nextAfter,nextAfter,truncated:false,events});

describe('MessageActivity', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<MessageActivity>>;
  const request = () => http.expectOne(r => r.url === endpoint);
  const apply = (value: string) => {
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input');
    input.value = value; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit',{cancelable:true}));
    vi.advanceTimersByTime(0);
  };
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document,'hidden',{configurable:true,value:false});
    TestBed.configureTestingModule({imports:[MessageActivity],providers:[provideHttpClient(),provideHttpClientTesting()]});
    http=TestBed.inject(HttpTestingController); fixture=TestBed.createComponent(MessageActivity);
    fixture.detectChanges();vi.advanceTimersByTime(0);
  });
  afterEach(() => {
    fixture.destroy();http.verify();vi.useRealTimers();
    Object.defineProperty(document,'hidden',{configurable:true,value:false});
  });
  it('uses the cursor, deduplicates by event ID and puts recent events first', () => {
    const first=request();expect(first.request.params.get('after')).toBe('0');
    first.flush(page([event('a',20)],20));
    vi.advanceTimersByTime(5000);
    const second=request();expect(second.request.params.get('after')).toBe('20');
    second.flush(page([event('a',20),event('b',30)],30));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('PUBLICATION_REQUESTED');
    vi.advanceTimersByTime(5000);
    const third=request();expect(third.request.params.get('after')).toBe('30');third.flush(page([],30));
  });
  it('resets filter cursor and cancels requests from the previous filter', () => {
    const old=request();
    apply('42');expect(old.cancelled).toBe(true);
    const filtered=request();expect(filtered.request.params.get('pedidoId')).toBe('42');
    expect(filtered.request.params.get('after')).toBe('0');filtered.flush(page([],0));
    apply('');const all=request();expect(all.request.params.has('pedidoId')).toBe(false);all.flush(page([],0));
  });
  it('validates IDs without calling the backend', () => {
    request().flush(page([],0));apply('-4');
    http.expectNone(r=>r.url===endpoint);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('entero positivo');
  });
  it('detects restart, discards prior events and requests cursor zero', () => {
    request().flush(page([event('old',30)],30));
    vi.advanceTimersByTime(5000);request().flush(page([],1,'instance-b'));
    fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Cambió la instancia');
    vi.advanceTimersByTime(5000);
    const fresh=request();expect(fresh.request.params.get('after')).toBe('0');
    fresh.flush(page([event('new',1)],1,'instance-b'));
    fixture.detectChanges();expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
  it('warns about retention gaps', () => {
    const truncated=page([event('a',100)],100);truncated.earliestSequence=100;truncated.truncated=true;
    request().flush(truncated);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('salto por retención');
  });
  it('backs off on errors without inventing events and allows manual update', () => {
    request().flush({}, {status:503,statusText:'Unavailable'});
    fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Actividad no disponible');
    vi.advanceTimersByTime(5000);http.expectNone(r=>r.url===endpoint);
    fixture.componentInstance.refresh();request().flush(page([],0));
  });
  it('pauses when hidden and prevents overlapping requests', () => {
    const pending=request();vi.advanceTimersByTime(5000);http.expectNone(r=>r.url===endpoint);
    pending.flush(page([],0));
    Object.defineProperty(document,'hidden',{configurable:true,value:true});
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(10000);http.expectNone(r=>r.url===endpoint);
    Object.defineProperty(document,'hidden',{configurable:true,value:false});
    document.dispatchEvent(new Event('visibilitychange'));
    const resumed=request();fixture.destroy();expect(resumed.cancelled).toBe(true);
  });
  it('stops automatic polling after forbidden response', () => {
    request().flush({}, {status:403,statusText:'Forbidden'});
    vi.advanceTimersByTime(120000);http.expectNone(r=>r.url===endpoint);
  });
});
