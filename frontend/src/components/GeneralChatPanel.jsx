import React, { useState, useEffect, useCallback, useRef, createElement } from 'react';
import {
  Box, Typography, Avatar, Chip, IconButton, Tooltip, Zoom, Fade, Alert,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import StopIcon from '@mui/icons-material/Stop';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckIcon from '@mui/icons-material/Check';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import MapIcon from '@mui/icons-material/Map';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import { useSSE } from '../hooks/useSSE.js';
import AgentTimeline from './AgentTimeline.jsx';

/* ─── Quick Prompts ─── */
const QUICK_PROMPTS = [
  { label: '解释设计模式', text: '请解释策略模式和观察者模式的区别，以及各自的适用场景' },
  { label: '代码优化技巧', text: '有哪些常见的代码优化技巧可以提升程序性能？' },
  { label: 'Git 最佳实践', text: '请介绍 Git 分支管理的最佳实践，包括 Git Flow 和 Trunk-based 开发' },
  { label: 'SQL 调优', text: '数据库查询慢的时候，应该从哪些方面排查和优化？' },
  { label: 'REST API 设计', text: 'RESTful API 设计有哪些核心原则和常见反模式？' },
  { label: '技术选型', text: '对于一个新项目的后端技术栈，Spring Boot 和 Go 各有什么优劣？' },
];

const GENERAL_CHAT_ENDPOINT = '/api/code/chat/general/stream';

/* ─── Time Formatter ─── */
function formatTime(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  if (isToday) return time;
  return `${d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })} ${time}`;
}

/* ─── Lightweight Markdown Renderer (same as ChatPanel) ─── */
function MarkdownContent({ text }) {
  if (!text) return null;

  const segments = [];
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match;

  while ((match = codeBlockRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', content: text.slice(lastIndex, match.index) });
    }
    segments.push({ type: 'code', lang: match[1] || 'plaintext', content: match[2].trimEnd() });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    segments.push({ type: 'text', content: text.slice(lastIndex) });
  }

  function renderInline(content) {
    const lines = content.split('\n');
    return lines.map((line, li) => {
      const hMatch = line.match(/^(#{1,3})\s+(.+)/);
      if (hMatch) {
        const level = hMatch[1].length;
        const sizes = ['1.05rem', '0.95rem', '0.87rem'];
        return createElement('div', {
          key: `h-${li}`,
          style: { fontWeight: 700, fontSize: sizes[level - 1] || '0.87rem', marginTop: li > 0 ? 12 : 0, marginBottom: 4, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' },
        }, parseInlineTokens(hMatch[2]));
      }

      const ulMatch = line.match(/^[-*]\s+(.+)/);
      if (ulMatch) {
        return createElement('div', {
          key: `ul-${li}`,
          style: { display: 'flex', gap: 8, marginTop: 2, marginBottom: 2, paddingLeft: 4 },
        },
          createElement('span', { style: { color: 'var(--accent)', flexShrink: 0, fontWeight: 700 } }, '·'),
          createElement('span', null, parseInlineTokens(ulMatch[1]))
        );
      }

      const olMatch = line.match(/^(\d+)\.\s+(.+)/);
      if (olMatch) {
        return createElement('div', {
          key: `ol-${li}`,
          style: { display: 'flex', gap: 8, marginTop: 2, marginBottom: 2, paddingLeft: 4 },
        },
          createElement('span', { style: { color: 'var(--accent)', flexShrink: 0, fontWeight: 600, minWidth: 16 } }, `${olMatch[1]}.`),
          createElement('span', null, parseInlineTokens(olMatch[2]))
        );
      }

      if (/^[-*_]{3,}$/.test(line.trim())) {
        return createElement('hr', { key: `hr-${li}`, style: { border: 'none', borderTop: '1px solid var(--border-subtle)', margin: '10px 0' } });
      }

      if (line.trim() === '') {
        return createElement('div', { key: `br-${li}`, style: { height: 8 } });
      }

      return createElement('div', { key: `p-${li}` }, parseInlineTokens(line));
    });
  }

  function parseInlineTokens(str) {
    const tokens = [];
    const regex = /(\*\*(.+?)\*\*)|(\*(.+?)\*)|(`([^`]+)`)|(\[([^\]]+)\]\(([^)]+)\))/g;
    let last = 0;
    let m;
    while ((m = regex.exec(str)) !== null) {
      if (m.index > last) tokens.push(createElement('span', { key: `t${last}` }, str.slice(last, m.index)));
      if (m[1] || m[3]) {
        const isBold = !!m[1];
        tokens.push(createElement('span', {
          key: `t${m.index}`,
          style: { fontWeight: isBold ? 700 : 400, fontStyle: isBold ? 'normal' : 'italic', color: 'var(--text-primary)' },
        }, m[2] || m[4]));
      } else if (m[5]) {
        tokens.push(createElement('code', {
          key: `t${m.index}`,
          style: {
            fontFamily: 'var(--font-code)', fontSize: '0.82em', padding: '2px 6px', borderRadius: 4,
            background: 'rgba(212,160,83,0.1)', color: 'var(--accent)',
          },
        }, m[6]));
      } else if (m[7]) {
        tokens.push(createElement('a', {
          key: `t${m.index}`,
          href: m[9], target: '_blank', rel: 'noopener',
          style: { color: 'var(--accent)', textDecoration: 'underline', fontWeight: 500 },
        }, m[8]));
      }
      last = m.index + m[0].length;
    }
    if (last < str.length) tokens.push(createElement('span', { key: `t${last}` }, str.slice(last)));
    return tokens.length === 0 ? str : tokens;
  }

  return createElement('div', { style: { lineHeight: 1.75 } },
    ...segments.map((seg, si) => {
      if (seg.type === 'code') {
        return createElement('div', { key: `seg${si}`, style: { margin: '8px 0', borderRadius: 8, overflow: 'hidden', border: '1px solid var(--border-subtle)' } },
          createElement('div', {
            style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px', background: 'rgba(0,0,0,0.4)', fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'var(--font-code)' },
          },
            createElement('span', null, seg.lang),
            createElement('span', { style: { cursor: 'pointer', color: 'var(--accent)' }, onClick: (e) => { navigator.clipboard.writeText(seg.content); e.target.textContent = '已复制'; setTimeout(() => { e.target.textContent = '复制'; }, 1500); } }, '复制')
          ),
          createElement('pre', {
            style: { margin: 0, padding: '12px 16px', overflow: 'auto', maxHeight: 300, background: 'rgba(0,0,0,0.3)', fontFamily: 'var(--font-code)', fontSize: '0.79rem', lineHeight: 1.65, color: 'var(--text-primary)' },
          }, seg.content)
        );
      }
      return createElement('div', { key: `seg${si}`, style: { fontSize: '0.85rem', color: 'var(--text-secondary)' } }, renderInline(seg.content));
    })
  );
}

/* ========================================================
   GeneralChatPanel Component
   ======================================================== */
export default function GeneralChatPanel({ darkMode }) {
  const [input, setInput] = useState('');
  const [planMode, setPlanMode] = useState(false);
  const [history, setHistory] = useState([]);
  const [inputError, setInputError] = useState(null);
  const [showScrollBtn, setShowScrollBtn] = useState(false);
  const [copiedIdx, setCopiedIdx] = useState(null);

  const chatAreaRef = useRef(null);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  const {
    messages: streamingMessage,
    isConnected,
    isLoading: streamLoading,
    error: streamError,
    traceEvents,
    startStream,
    stopStream,
  } = useSSE();

  const [timelineOpen, setTimelineOpen] = useState(false);

  // Auto-open timeline when first trace event arrives
  useEffect(() => {
    if (traceEvents.length > 0 && !timelineOpen) {
      setTimelineOpen(true);
    }
  }, [traceEvents.length]);

  const scrollToBottom = useCallback((smooth = true) => {
    messagesEndRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [streamingMessage, history, scrollToBottom]);

  useEffect(() => {
    const el = chatAreaRef.current;
    if (!el) return;
    const handleScroll = () => {
      const diff = el.scrollHeight - el.scrollTop - el.clientHeight;
      setShowScrollBtn(diff > 80);
    };
    el.addEventListener('scroll', handleScroll, { passive: true });
    return () => el.removeEventListener('scroll', handleScroll);
  }, []);

  // Focus input after mount
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleSend = useCallback((text) => {
    const msg = (text || input).trim();
    if (!msg) { setInputError('请输入您的问题'); return; }
    setInputError(null);
    setHistory((prev) => [...prev, { role: 'user', content: msg, timestamp: Date.now() }]);
    startStream(msg, null, GENERAL_CHAT_ENDPOINT, planMode);
    setInput('');
  }, [input, startStream]);

  const handleQuickPrompt = useCallback((text) => {
    if (isConnected || streamLoading) return;
    handleSend(text);
  }, [isConnected, streamLoading, handleSend]);

  // Move completed stream to history
  useEffect(() => {
    if (!isConnected && !streamLoading && streamingMessage) {
      setHistory((prev) => [...prev, { role: 'assistant', content: streamingMessage, timestamp: Date.now() }]);
    }
  }, [isConnected, streamLoading, streamingMessage]);

  const handleCopyMsg = (content, idx) => {
    navigator.clipboard.writeText(content).then(() => {
      setCopiedIdx(idx);
      setTimeout(() => setCopiedIdx(null), 2000);
    }).catch(() => {});
  };

  const handleRegenerate = useCallback(() => {
    if (isConnected || streamLoading) return;
    const lastUserMsg = [...history].reverse().find(m => m.role === 'user');
    if (lastUserMsg) {
      startStream(lastUserMsg.content, null, GENERAL_CHAT_ENDPOINT, planMode);
    }
  }, [isConnected, streamLoading, history, startStream]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isConnected && !streamLoading) handleSend();
    }
  };

  const isStreaming = isConnected || streamLoading;

  return (
    <Box sx={{
      display: 'flex', flexDirection: 'column',
      height: '100%', minHeight: 560,
      background: 'var(--bg-surface)',
      border: '1px solid var(--border-subtle)',
      borderRadius: 'var(--radius-card)',
      overflow: 'hidden',
    }}>
      {/* ── Chat Area ── */}
      <Box
        ref={chatAreaRef}
        className="custom-scrollbar"
        sx={{
          flex: 1, overflow: 'auto', px: { xs: 2, sm: 3 }, py: 2,
          position: 'relative',
        }}
      >
        {/* Empty state */}
        {history.length === 0 && !isStreaming && (
          <Box sx={{
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', height: '100%', minHeight: 300, gap: 3,
          }}>
            <Box sx={{
              width: 64, height: 64, borderRadius: 3,
              background: 'linear-gradient(135deg, rgba(45,212,191,0.12), rgba(212,160,83,0.08))',
              border: '1px solid rgba(45,212,191,0.15)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <AutoAwesomeIcon sx={{ fontSize: 28, color: 'var(--accent-secondary)' }} />
            </Box>
            <Box sx={{ textAlign: 'center' }}>
              <Typography sx={{
                fontFamily: 'var(--font-display)',
                fontSize: '1.1rem', fontWeight: 700,
                color: 'var(--text-primary)', mb: 0.5,
              }}>
                和 AI 聊聊吧
              </Typography>
              <Typography sx={{ fontSize: '0.82rem', color: 'var(--text-muted)', maxWidth: 320 }}>
                不需要绑定代码仓库，自由提问任何问题
              </Typography>
            </Box>

            {/* Quick prompt chips */}
            <Box sx={{
              display: 'flex', flexWrap: 'wrap', gap: 1,
              justifyContent: 'center', maxWidth: 540,
            }}>
              {QUICK_PROMPTS.map((qp) => (
                <Chip
                  key={qp.label}
                  label={qp.label}
                  size="small"
                  variant="outlined"
                  onClick={() => handleQuickPrompt(qp.text)}
                  sx={{
                    borderRadius: 2,
                    borderColor: 'rgba(45,212,191,0.2)',
                    color: 'var(--accent-secondary)',
                    fontWeight: 500, fontSize: '0.75rem',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    '&:hover': {
                      bgcolor: 'rgba(45,212,191,0.08)',
                      borderColor: 'var(--accent-secondary)',
                    },
                  }}
                />
              ))}
            </Box>
          </Box>
        )}

        {/* Message history */}
        {history.map((msg, idx) => (
          <Box key={idx} sx={{
            mb: 3, display: 'flex', gap: 1.5,
            flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
          }}>
            <Avatar sx={{
              width: 30, height: 30, mt: 0.3, flexShrink: 0,
              background: msg.role === 'user'
                ? 'linear-gradient(135deg, var(--accent), var(--accent-hover))'
                : 'linear-gradient(135deg, var(--accent-secondary), #0d9488)',
              color: '#0c0b0e', fontSize: '0.72rem', fontWeight: 700,
            }}>
              {msg.role === 'user' ? 'You' : <SmartToyIcon sx={{ fontSize: 16 }} />}
            </Avatar>

            <Box sx={{ maxWidth: '85%' }}>
              {/* Meta */}
              <Box sx={{
                display: 'flex', alignItems: 'center', gap: 1, mb: 0.4,
                flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
              }}>
                <Typography variant="caption" sx={{
                  fontWeight: 700, fontSize: '0.7rem',
                  color: msg.role === 'user' ? 'var(--accent)' : 'var(--accent-secondary)',
                }}>
                  {msg.role === 'user' ? '你' : 'AI 助手'}
                </Typography>
                <Typography variant="caption" sx={{ color: 'var(--text-muted)', fontSize: '0.65rem' }}>
                  {formatTime(msg.timestamp)}
                </Typography>
              </Box>

              {/* Bubble */}
              <div className={msg.role === 'user' ? 'chat-bubble-user' : 'chat-bubble-ai'}>
                {msg.role === 'assistant' ? (
                  <MarkdownContent text={msg.content} />
                ) : (
                  <Typography variant="body2" sx={{
                    whiteSpace: 'pre-wrap', lineHeight: 1.65, fontSize: '0.85rem',
                    color: 'var(--text-primary)',
                  }}>
                    {msg.content}
                  </Typography>
                )}
              </div>

              {/* Actions for AI messages */}
              {msg.role === 'assistant' && (
                <Box sx={{ display: 'flex', gap: 0.5, mt: 0.5 }}>
                  <Tooltip title="复制" arrow>
                    <IconButton
                      size="small"
                      onClick={() => handleCopyMsg(msg.content, idx)}
                      sx={{ color: copiedIdx === idx ? 'var(--success)' : 'var(--text-muted)', '&:hover': { color: 'var(--accent)' } }}
                    >
                      {copiedIdx === idx ? <CheckIcon sx={{ fontSize: 14 }} /> : <ContentCopyIcon sx={{ fontSize: 14 }} />}
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="重新生成" arrow>
                    <IconButton
                      size="small"
                      onClick={handleRegenerate}
                      disabled={isStreaming}
                      sx={{ color: 'var(--text-muted)', '&:hover': { color: 'var(--accent)' } }}
                    >
                      <RefreshIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                  </Tooltip>
                </Box>
              )}
            </Box>
          </Box>
        ))}

        {/* Streaming bubble */}
        {isStreaming && streamingMessage && (
          <Box sx={{ mb: 3, display: 'flex', gap: 1.5 }}>
            <Avatar sx={{
              width: 30, height: 30, flexShrink: 0,
              background: 'linear-gradient(135deg, var(--accent-secondary), #0d9488)',
              color: '#0c0b0e',
            }}>
              <SmartToyIcon sx={{ fontSize: 16 }} />
            </Avatar>
            <Box sx={{ maxWidth: '85%' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4 }}>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'var(--accent-secondary)', fontSize: '0.7rem' }}>AI 助手</Typography>
                <Box className="thinking-dots" sx={{ display: 'inline-flex', gap: '3px' }}>
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
              </Box>
              <div className="chat-bubble-ai">
                <MarkdownContent text={streamingMessage} />
                <Box component="span" sx={{
                  display: 'inline-block', width: 7, height: 15,
                  bgcolor: 'var(--accent)', ml: 0.3, borderRadius: 1.5,
                  animation: 'cursorBlink 1s step-end infinite', verticalAlign: 'text-bottom',
                }} />
              </div>
            </Box>
          </Box>
        )}

        {/* Thinking state */}
        {streamLoading && !streamingMessage && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 2, ml: 1 }}>
            <Box sx={{
              width: 30, height: 30, borderRadius: 1.5,
              bgcolor: 'rgba(45,212,191,0.1)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <SmartToyIcon sx={{ fontSize: 16, color: 'var(--accent-secondary)' }} />
            </Box>
            <Box className="thinking-dots">
              <Box component="span" className="dot" />
              <Box component="span" className="dot" />
              <Box component="span" className="dot" />
            </Box>
            <Typography variant="body2" sx={{ color: 'var(--text-muted)', ml: 0.5, fontSize: '0.82rem' }}>
              思考中...
            </Typography>
          </Box>
        )}

        {streamError && (
          <Fade in>
            <Alert severity="error" sx={{ mt: 2, borderRadius: 2 }}>{streamError}</Alert>
          </Fade>
        )}

        {/* Agent execution timeline */}
        {traceEvents.length > 0 && (
          <AgentTimeline
            traceEvents={traceEvents}
            collapsed={!timelineOpen}
            onToggle={() => setTimelineOpen((prev) => !prev)}
          />
        )}

        <div ref={messagesEndRef} />
      </Box>

      {/* Scroll to bottom FAB */}
      <Zoom in={showScrollBtn}>
        <Box
          onClick={() => scrollToBottom()}
          sx={{
            position: 'absolute', bottom: 80, right: 24,
            width: 34, height: 34, borderRadius: '50%',
            bgcolor: 'var(--accent)',
            color: '#0c0b0e',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 12px var(--accent-glow)',
            transition: 'all 0.2s',
            '&:hover': { transform: 'scale(1.1)', boxShadow: '0 6px 16px var(--accent-glow)' },
            zIndex: 10,
          }}
        >
          <KeyboardArrowDownIcon sx={{ fontSize: 20 }} />
        </Box>
      </Zoom>

      {/* ── Input Area ── */}
      <Box sx={{
        px: { xs: 2, sm: 3 }, py: 2,
        borderTop: '1px solid var(--border-subtle)',
        background: darkMode ? 'rgba(10,9,12,0.6)' : 'rgba(255,255,255,0.6)',
      }}>
        {inputError && (
          <Alert severity="warning" sx={{ mb: 1.5, borderRadius: 2, py: 0 }} onClose={() => setInputError(null)}>
            <Typography sx={{ fontSize: '0.8rem' }}>{inputError}</Typography>
          </Alert>
        )}

        {/* ── Plan Mode Toggle (compact) ── */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
          <Box sx={{ display: 'flex', gap: 0 }}>
            {/* 快速模式 */}
            <Box
              onClick={() => !isStreaming && setPlanMode(false)}
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.5,
                px: 1.2, py: 0.35,
                borderTopLeftRadius: 'var(--radius-btn)',
                borderBottomLeftRadius: 'var(--radius-btn)',
                border: '1px solid var(--border-subtle)',
                borderRight: 'none',
                bgcolor: !planMode ? 'rgba(45,212,191,0.1)' : 'transparent',
                cursor: isStreaming ? 'default' : 'pointer',
                opacity: isStreaming ? 0.5 : 1,
                transition: 'all 0.2s',
                '&:hover': !isStreaming && planMode ? { bgcolor: 'rgba(45,212,191,0.05)' } : {},
              }}
            >
              <RocketLaunchIcon sx={{
                fontSize: 13,
                color: !planMode ? 'var(--accent-secondary)' : 'var(--text-muted)',
              }} />
              <Typography sx={{
                fontSize: '0.7rem', fontWeight: !planMode ? 700 : 500,
                color: !planMode ? 'var(--accent-secondary)' : 'var(--text-muted)',
                fontFamily: 'var(--font-body)',
              }}>
                快速
              </Typography>
            </Box>
            {/* 规划模式 */}
            <Box
              onClick={() => !isStreaming && setPlanMode(true)}
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.5,
                px: 1.2, py: 0.35,
                borderTopRightRadius: 'var(--radius-btn)',
                borderBottomRightRadius: 'var(--radius-btn)',
                border: '1px solid var(--border-subtle)',
                bgcolor: planMode ? 'rgba(212,160,83,0.1)' : 'transparent',
                cursor: isStreaming ? 'default' : 'pointer',
                opacity: isStreaming ? 0.5 : 1,
                transition: 'all 0.2s',
                '&:hover': !isStreaming && !planMode ? { bgcolor: 'rgba(212,160,83,0.05)' } : {},
              }}
            >
              <MapIcon sx={{
                fontSize: 13,
                color: planMode ? 'var(--accent)' : 'var(--text-muted)',
              }} />
              <Typography sx={{
                fontSize: '0.7rem', fontWeight: planMode ? 700 : 500,
                color: planMode ? 'var(--accent)' : 'var(--text-muted)',
                fontFamily: 'var(--font-body)',
              }}>
                规划
              </Typography>
            </Box>
          </Box>
          {planMode && (
            <Chip
              label="规划模式"
              size="small"
              icon={<MapIcon sx={{ fontSize: 11 }} />}
              sx={{
                height: 22, fontSize: '0.65rem',
                bgcolor: 'rgba(212,160,83,0.1)',
                color: 'var(--accent)',
                fontWeight: 600,
                border: '1px solid rgba(212,160,83,0.2)',
              }}
            />
          )}
        </Box>

        <Box sx={{
          display: 'flex', alignItems: 'flex-end', gap: 1.2,
          p: '6px 6px 6px 16px',
          borderRadius: 'var(--radius-card)',
          border: '1px solid var(--border-subtle)',
          bgcolor: darkMode ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
          transition: 'border-color 0.2s',
          '&:focus-within': { borderColor: 'var(--accent-secondary)' },
        }}>
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isStreaming}
            placeholder="输入你的问题... (Shift+Enter 换行)"
            rows={1}
            style={{
              flex: 1,
              padding: '10px 0',
              border: 'none',
              background: 'transparent',
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-body)',
              fontSize: '0.88rem',
              lineHeight: 1.5,
              outline: 'none',
              resize: 'none',
              maxHeight: 120,
              minHeight: 24,
            }}
            onInput={(e) => {
              e.target.style.height = 'auto';
              e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px';
            }}
          />

          {isStreaming ? (
            <Box
              onClick={stopStream}
              sx={{
                width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                bgcolor: 'var(--danger)',
                color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': { opacity: 0.85, transform: 'scale(1.05)' },
              }}
            >
              <StopIcon sx={{ fontSize: 18 }} />
            </Box>
          ) : (
            <Box
              onClick={() => handleSend()}
              sx={{
                width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                background: input.trim()
                  ? 'linear-gradient(135deg, var(--accent-secondary), #0d9488)'
                  : 'var(--border-subtle)',
                color: input.trim() ? '#fff' : 'var(--text-muted)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: input.trim() ? 'pointer' : 'default',
                transition: 'all 0.2s',
                '&:hover': input.trim() ? { transform: 'scale(1.05)', boxShadow: '0 4px 12px var(--accent-secondary-glow)' } : {},
              }}
            >
              <SendIcon sx={{ fontSize: 17 }} />
            </Box>
          )}
        </Box>

        <Typography sx={{
          fontSize: '0.65rem', color: 'var(--text-muted)',
          textAlign: 'center', mt: 1, opacity: 0.6,
        }}>
          Enter 发送 · Shift+Enter 换行 · AI 回答仅供参考
        </Typography>
      </Box>
    </Box>
  );
}
