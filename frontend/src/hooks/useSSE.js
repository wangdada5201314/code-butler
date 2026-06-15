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
 *   errorType: string | null,
 *   phase: string,
 *   toolCallCount: number,
 *   startStream: (question: string, repoPath: string, endpoint?: string) => void,
 *   stopStream: () => void
 * }}
 */

/* ─── Error type mapping ─── */
function categorizeError(errMsg, statusCode) {
  if (statusCode === 401 || errMsg.includes('40100') || errMsg.includes('登录'))
    return { type: 'auth', icon: '\u{1F512}', title: '未登录', hint: '请先登录后再使用审查功能' };
  if (errMsg.includes('40301') || errMsg.includes('配额'))
    return { type: 'quota', icon: '\u{1F4CA}', title: '配额超限', hint: '今日审查次数已用完，请明天再试' };
  if (errMsg.includes('timeout') || errMsg.includes('Timeout'))
    return { type: 'timeout', icon: '\u23F0', title: '请求超时', hint: '仓库较大时审查可能需要更长时间，请稍后重试' };
  if (statusCode >= 500 || errMsg.includes('50000'))
    return { type: 'server', icon: '\u{1F6A8}', title: '服务端异常', hint: '服务器出现错误，请稍后重试或联系管理员' };
  if (errMsg.includes('network') || errMsg.includes('fetch') || errMsg.includes('连接'))
    return { type: 'network', icon: '\u{1F310}', title: '网络异常', hint: '无法连接到服务器，请检查网络连接' };
  if (errMsg.includes('aborted') || errMsg.includes('AbortError'))
    return { type: 'aborted', icon: '\u23F9\uFE0F', title: '已取消', hint: '' };
  return { type: 'unknown', icon: '\u26A0\uFE0F', title: '发生错误', hint: errMsg || '未知错误' };
}

export function useSSE() {
  const [messages, setMessages] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [errorType, setErrorType] = useState(null);
  const [traceEvents, setTraceEvents] = useState([]);
  const [summary, setSummary] = useState(null);
  const [phase, setPhase] = useState('idle');
  const [toolCallCount, setToolCallCount] = useState(0);

  const abortControllerRef = useRef(null);
  const readerRef = useRef(null);
  const hasTextRef = useRef(false);

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
    setPhase('idle');
  }, []);

  /**
   * Process a complete SSE event (event type + data).
   */
  const processEvent = useCallback((eventType, data) => {
    if (!data) return;

    if (eventType === 'done' || data === '[DONE]') {
      setIsConnected(false);
      setPhase('complete');
      return;
    }

    if (eventType === 'error' || data.startsWith('[ERROR]')) {
      const errMsg = data.replace('[ERROR] ', '');
      const categorized = categorizeError(errMsg, 0);
      setError(categorized.hint || errMsg);
      setErrorType(categorized);
      setIsConnected(false);
      setPhase('idle');
      return;
    }

    // Agent 追踪事件：解析 JSON 并追加到 traceEvents
    if (eventType === 'trace') {
      try {
        const traceEvent = JSON.parse(data);
        setTraceEvents((prev) => [...prev, traceEvent]);
        // Phase tracking from trace events
        if (traceEvent.type === 'TOOL_CALL_START') {
          setToolCallCount((prev) => {
            const next = prev + 1;
            if (next === 1) setPhase('scanning');
            else setPhase('analyzing');
            return next;
          });
        }
        if (traceEvent.type === 'REASONING_START') {
          setPhase('reporting');
        }
      } catch (e) {
        // JSON 解析失败则忽略
      }
      return;
    }

    // 结构化审查摘要
    if (eventType === 'summary') {
      try {
        const issues = JSON.parse(data);
        setSummary(issues);
      } catch (e) {
        // 忽略
      }
      return;
    }

    // For 'tool' events and default data events, append to messages
    if (!hasTextRef.current) {
      hasTextRef.current = true;
      setPhase('reporting');
    }
    setMessages((prev) => prev + data);
  }, []);

  const startStream = useCallback((question, repoPath, endpoint, planMode = false) => {
    // Reset state
    setMessages('');
    setError(null);
    setErrorType(null);
    setTraceEvents([]);
    setSummary(null);
    setToolCallCount(0);
    setIsLoading(true);
    setIsConnected(false);
    setPhase('connecting');
    hasTextRef.current = false;

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
      ? JSON.stringify({ question, repoPath, planMode })
      : JSON.stringify({ message: question, planMode });

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
          const categorized = categorizeError(errorResult.message || '', errorResult.code);
          if (errorResult.code === 40100) {
            window.dispatchEvent(new CustomEvent('auth:required'));
            setErrorType(categorized);
            throw new Error(errorResult.message || '请先登录');
          }
          if (errorResult.code === 40301) {
            setErrorType(categorized);
            throw new Error(errorResult.message || '配额已用完');
          }
          setErrorType(categorized);
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
          setPhase('idle');
          return;
        }
        const categorized = categorizeError(err.message || '', 0);
        setErrorType(categorized);
        setError(categorized.hint || err.message || 'Stream connection failed');
        setIsConnected(false);
        setIsLoading(false);
        setPhase('idle');
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
    errorType,
    phase,
    toolCallCount,
    traceEvents,
    summary,
    startStream,
    stopStream,
  };
}
