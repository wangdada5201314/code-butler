import { useState, useRef, useCallback, useEffect } from 'react';

/**
 * Custom hook for SSE (Server-Sent Events) streaming using fetch + ReadableStream.
 *
 * Standard EventSource only supports GET requests. The backend expects POST with JSON body,
 * so this hook uses fetch with ReadableStream to handle text/event-stream responses.
 *
 * Supports both standard ServerSentEvent format (event: + data: lines) and
 * simple data-only format for backward compatibility.
 *
 * @returns {{
 *   messages: string,
 *   isConnected: boolean,
 *   isLoading: boolean,
 *   error: string | null,
 *   startStream: (question: string, repoPath: string, endpoint?: string) => void,
 *   stopStream: () => void
 * }}
 */
export function useSSE() {
  const [messages, setMessages] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const abortControllerRef = useRef(null);
  const readerRef = useRef(null);

  const stopStream = useCallback(() => {
    if (readerRef.current) {
      readerRef.current.cancel().catch(() => {});
      readerRef.current = null;
    }
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsConnected(false);
    setIsLoading(false);
  }, []);

  /**
   * Process a complete SSE event (event type + data).
   */
  const processEvent = useCallback((eventType, data) => {
    if (!data) return;

    if (eventType === 'done' || data === '[DONE]') {
      setIsConnected(false);
      return;
    }

    if (eventType === 'error' || data.startsWith('[ERROR]')) {
      setError(data.replace('[ERROR] ', ''));
      setIsConnected(false);
      return;
    }

    // For 'tool' events and default data events, append to messages
    setMessages((prev) => prev + data);
  }, []);

  const startStream = useCallback((question, repoPath, endpoint) => {
    // Reset state
    setMessages('');
    setError(null);
    setIsLoading(true);
    setIsConnected(false);

    // Stop any existing stream
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;

    const API_BASE = '/api';
    const url = endpoint || `${API_BASE}/code/chat/stream`;

    // Build body: for general chat (no repoPath), only send message
    const body = repoPath
      ? JSON.stringify({ question, repoPath })
      : JSON.stringify({ message: question });

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: body,
      signal: controller.signal,
      credentials: 'include',
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        // Check if backend returned JSON error (e.g., 401 not logged in) instead of SSE stream
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const errorResult = await response.json();
          if (errorResult.code === 40100) {
            window.dispatchEvent(new CustomEvent('auth:required'));
            throw new Error(errorResult.message || '请先登录');
          }
          throw new Error(errorResult.message || 'Request failed');
        }

        setIsConnected(true);
        setIsLoading(false);

        const reader = response.body.getReader();
        readerRef.current = reader;
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        // SSE state: track current event type and accumulated data
        let currentEvent = '';
        let currentData = '';

        const flushEvent = () => {
          if (currentData || currentEvent) {
            processEvent(currentEvent, currentData);
            currentEvent = '';
            currentData = '';
          }
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });

          // Split by double newline (SSE event separator)
          const events = buffer.split('\n\n');
          buffer = events.pop() || ''; // Keep incomplete event in buffer

          for (const eventBlock of events) {
            if (!eventBlock.trim()) continue;

            currentEvent = '';
            currentData = '';

            const lines = eventBlock.split('\n');
            for (const line of lines) {
              if (line.startsWith('event:')) {
                currentEvent = line.slice(6).trim();
              } else if (line.startsWith('data:')) {
                const dataContent = line.slice(5).trimStart();
                currentData += (currentData ? '\n' : '') + dataContent;
              }
            }

            flushEvent();
          }
        }

        // Process remaining buffer
        if (buffer.trim()) {
          currentEvent = '';
          currentData = '';
          const lines = buffer.split('\n');
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const dataContent = line.slice(5).trimStart();
              currentData += (currentData ? '\n' : '') + dataContent;
            }
          }
          flushEvent();
        }

        setIsConnected(false);
      })
      .catch((err) => {
        if (err.name === 'AbortError') {
          setIsConnected(false);
          setIsLoading(false);
          return;
        }
        setError(err.message || 'Stream connection failed');
        setIsConnected(false);
        setIsLoading(false);
      });
  }, [processEvent]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopStream();
    };
  }, [stopStream]);

  return {
    messages,
    isConnected,
    isLoading,
    error,
    startStream,
    stopStream,
  };
}
