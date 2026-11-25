export interface MessagingEvent {
  eventId: string; sequence: number; timestamp: string; pedidoId: number | null;
  exchange: string | null; routingKey: string | null; queue: string | null;
  consumer: string | null; attempt: number | null; type: string; result: string;
}
export interface MessagingEventsPage {
  instanceId: string; capacity: number; earliestSequence: number; lastSequence: number;
  nextAfter: number; truncated: boolean; events: MessagingEvent[];
}
