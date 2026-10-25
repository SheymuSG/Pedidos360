/** Estado de un caso de uso de mensajería: cola principal + su DLQ (GET /api/monitoring/queues). */
export interface ColaEstado {
  queue: string;
  queueMessages: number;
  queueConsumers: number;
  dlq: string;
  dlqMessages: number;
  alert: boolean;
  /** OK | ALERTA | NO_DISPONIBLE */
  state: string;
}
