import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, defer, EMPTY, exhaustMap, filter, finalize, fromEvent, merge, Subject, switchMap, startWith, tap, timer } from 'rxjs';
import { MessagingEventsService } from '../../services/messaging-events.service';
import { MessagingEvent, MessagingEventsPage } from '../../models/messaging-events';

@Component({
  selector: 'app-message-activity', standalone: true, imports: [DatePipe],
  templateUrl: './message-activity.html', styleUrl: './message-activity.css'
})
export class MessageActivity implements OnInit {
  private readonly api = inject(MessagingEventsService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly manual = new Subject<void>();
  private readonly filters = new Subject<void>();
  private cursor = 0;
  private instance: string | null = null;
  private pedidoId: number | null = null;
  private failures = 0;
  private nextAttempt = 0;
  private blocked = false;
  protected draft = '';
  protected readonly events = signal<MessagingEvent[]>([]);
  protected readonly loading = signal(false);
  protected readonly attempted = signal(false);
  protected readonly error = signal('');
  protected readonly filterError = signal('');
  protected readonly restart = signal(false);
  protected readonly retention = signal(false);
  protected readonly updatedAt = signal<Date | null>(null);
  protected readonly hidden = signal(this.document.hidden);

  ngOnInit(): void {
    const visible = fromEvent(this.document, 'visibilitychange').pipe(
      tap(() => this.hidden.set(this.document.hidden)), filter(() => !this.document.hidden));
    this.filters.pipe(startWith(undefined), switchMap(() =>
      merge(timer(0, 5000), this.manual, visible).pipe(
        filter(() => !this.document.hidden && !this.blocked && Date.now() >= this.nextAttempt),
        exhaustMap(() => defer(() => {
          this.loading.set(true);
          return this.api.getEvents(this.cursor, this.pedidoId).pipe(
            tap(page => this.accept(page)),
            catchError((error: unknown) => {
              this.failures++;
              this.nextAttempt = Date.now() + Math.min(60000, 5000 * 2 ** Math.min(this.failures, 4));
              this.blocked = error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
              this.error.set(this.blocked ? 'No tienes acceso al historial. Se requiere una sesión Admin válida.'
                : 'No se pudo actualizar la actividad. Los eventos visibles pueden estar desactualizados; se reintentará con espera progresiva.');
              return EMPTY;
            }),
            finalize(() => { this.loading.set(false); this.attempted.set(true); })
          );
        }))
      )), takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  private accept(page: MessagingEventsPage): void {
    if (!page.instanceId || !Array.isArray(page.events) || !Number.isSafeInteger(page.nextAfter) || page.nextAfter < 0) {
      throw new Error('Invalid history response');
    }
    this.error.set(''); this.failures = 0; this.nextAttempt = 0;
    this.updatedAt.set(new Date());
    if (this.instance !== null && this.instance !== page.instanceId) {
      this.restart.set(true); this.events.set([]); this.retention.set(false);
      this.instance = page.instanceId;
      // The response was requested with the old instance cursor. Fetch again from zero.
      this.cursor = 0;
      return;
    }
    this.instance = page.instanceId;
    if (page.truncated || (this.cursor > 0 && this.cursor < page.earliestSequence - 1)) this.retention.set(true);
    const retained = this.events().filter(event => event.sequence >= page.earliestSequence);
    const unique = new Map(retained.map(event => [event.eventId, event]));
    for (const event of page.events) unique.set(event.eventId, event);
    this.events.set([...unique.values()].sort((a, b) =>
      Date.parse(b.timestamp) - Date.parse(a.timestamp) || b.sequence - a.sequence).slice(0, 1000));
    this.cursor = page.nextAfter;
  }

  refresh(): void {
    if (this.loading() || this.document.hidden) return;
    this.nextAttempt = 0; this.blocked = false; this.manual.next();
  }

  protected applyFilter(): void {
    const text = this.draft.trim();
    if (text && (!/^[0-9]+$/.test(text) || !Number.isSafeInteger(Number(text)) || Number(text) <= 0)) {
      this.filterError.set('Ingresa un ID de pedido entero positivo.'); return;
    }
    this.filterError.set(''); this.pedidoId = text ? Number(text) : null;
    // switchMap cancels any request for the previous filter.
    this.cursor = 0; this.instance = null; this.events.set([]);
    this.error.set(''); this.restart.set(false); this.retention.set(false);
    this.failures = 0; this.nextAttempt = 0; this.blocked = false;
    this.updatedAt.set(null); this.attempted.set(false); this.filters.next();
  }

  protected tone(type: string): string {
    if (type === 'NACK_SENT' || type === 'PROCESSING_ERROR') return 'failure';
    if (type === 'ACK_SENT' || type === 'PROCESSING_SUCCEEDED') return 'success';
    return type === 'RECEIVED' ? 'received' : 'processing';
  }
}
