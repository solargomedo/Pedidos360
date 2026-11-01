export type MonitorStatus = 'available' | 'partial' | 'unavailable';
export interface Section<T> { resource: string; status: string; data: T | null; error: string | null; }
export interface RabbitNode { name: string; running: boolean | null; mem_alarm: boolean | null; disk_free_alarm: boolean | null; partitions: string[] | null; }
export interface RabbitQueue { name: string; messages_ready: number | null; messages_unacknowledged: number | null; messages: number | null; consumers: number | null; }
export interface RabbitExchange { name: string; type: string | null; durable: boolean | null; }
export interface RabbitBinding { source: string; destination: string; destination_type: string; routing_key: string; }
export interface Monitor {
  queriedAt: string; status: MonitorStatus;
  nodes: Section<RabbitNode[]> | null; queues: Section<RabbitQueue>[] | null;
  exchanges: Section<RabbitExchange>[] | null; bindings: Section<RabbitBinding[]> | null;
}
