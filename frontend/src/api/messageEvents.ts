// Authenticated Server-Sent Events client for message notifications.
// The JWT is sent via the Authorization header, never in the query string.

export interface MessageStreamHandlers {
  signal: AbortSignal;
  onOpen?: () => void;
  onEvent?: (event: string, data: unknown) => void;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

export async function streamMessageEvents({ signal, onOpen, onEvent }: MessageStreamHandlers): Promise<void> {
  const token = localStorage.getItem('token');
  const response = await fetch(`${API_BASE}/messages/events`, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`Message event stream failed (${response.status})`);
  }

  onOpen?.();

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let boundary = buffer.indexOf('\n\n');
    while (boundary !== -1) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      dispatchFrame(frame, onEvent);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

function dispatchFrame(frame: string, onEvent?: (event: string, data: unknown) => void): void {
  let eventName = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (dataLines.length === 0) return;
  const raw = dataLines.join('\n');
  let parsed: unknown = raw;
  try {
    parsed = JSON.parse(raw);
  } catch {
    // Leave as plain string when the payload is not JSON.
  }
  onEvent?.(eventName, parsed);
}
