import React, { useState, useEffect, useCallback, useRef, createElement } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, Paper, Avatar, Chip,
  IconButton, Tooltip, Fade, Zoom, Alert,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import StopIcon from '@mui/icons-material/Stop';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PsychologyIcon from '@mui/icons-material/Psychology';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckIcon from '@mui/icons-material/Check';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import MapIcon from '@mui/icons-material/Map';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import { useSSE } from '../hooks/useSSE.js';
import FavoriteReposBar from './FavoriteReposBar.jsx';
import AgentTimeline from './AgentTimeline.jsx';

/* ─── Quick Prompts ─── */
const QUICK_PROMPTS = [
  { label: '分析项目结构', text: '请分析这个项目的目录结构和模块划分' },
  { label: '查找潜在Bug', text: '请扫描代码中可能存在的Bug和安全隐患' },
  { label: '优化建议', text: '请对当前代码提出性能优化和改进建议' },
  { label: '生成API文档', text: '请为这个项目的主要接口生成API文档' },
];

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

/* ─── Lightweight Markdown Renderer ─── */
function MarkdownContent({ text, darkMode }) {
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
   ChatPanel Component
   ======================================================== */
export default function ChatPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [question, setQuestion] = useState('');
  const [planMode, setPlanMode] = useState(false);
  const [history, setHistory] = useState([]);
  const [inputError, setInputError] = useState(null);
  const [showScrollBtn, setShowScrollBtn] = useState(false);
  const [copiedIdx, setCopiedIdx] = useState(null);

  const chatAreaRef = useRef(null);
  const messagesEndRef = useRef(null);

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

  const handleSend = useCallback((text) => {
    const msg = (text || question).trim();
    if (!repoPath.trim()) { setInputError('请输入代码仓库路径'); return; }
    if (!msg) { setInputError('请输入您的问题'); return; }
    setInputError(null);
    setHistory((prev) => [...prev, { role: 'user', content: msg, timestamp: Date.now() }]);
    startStream(msg, repoPath.trim(), undefined, planMode);
    setQuestion('');
  }, [repoPath, question, startStream]);

  const handleQuickPrompt = useCallback((prompt) => {
    if (isConnected || streamLoading) return;
    setQuestion(prompt.text);
    setTimeout(() => handleSend(prompt.text), 50);
  }, [isConnected, streamLoading, handleSend]);

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
      startStream(lastUserMsg.content, repoPath.trim(), undefined, planMode);
    }
  }, [isConnected, streamLoading, history, repoPath, startStream]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isConnected && !streamLoading) handleSend();
    }
  };

  const isStreaming = isConnected || streamLoading;

  return (
    <div className="forge-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ p: '22px 24px !important', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2, mb: 2.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: 'rgba(45,212,191,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <PsychologyIcon sx={{ color: 'var(--accent-secondary)', fontSize: 18 }} />
          </Box>
          <Typography className="section-title">智能问答</Typography>

          {/* ── Plan Mode Toggle ── */}
          <Box sx={{ ml: 'auto', display: 'flex', gap: 0 }}>
            {/* 快速模式 */}
            <Box
              onClick={() => !isStreaming && setPlanMode(false)}
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.5,
                px: 1.5, py: 0.5,
                borderTopLeftRadius: 'var(--radius-btn)',
                borderBottomLeftRadius: 'var(--radius-btn)',
                border: '1px solid var(--border-subtle)',
                borderRight: 'none',
                bgcolor: !planMode ? 'rgba(45,212,191,0.12)' : 'transparent',
                cursor: isStreaming ? 'default' : 'pointer',
                opacity: isStreaming ? 0.5 : 1,
                transition: 'all 0.2s',
                '&:hover': !isStreaming && planMode ? { bgcolor: 'rgba(45,212,191,0.06)' } : {},
              }}
            >
              <RocketLaunchIcon sx={{
                fontSize: 14,
                color: !planMode ? 'var(--accent-secondary)' : 'var(--text-muted)',
              }} />
              <Typography sx={{
                fontSize: '0.72rem', fontWeight: !planMode ? 700 : 500,
                color: !planMode ? 'var(--accent-secondary)' : 'var(--text-muted)',
                fontFamily: 'var(--font-body)', whiteSpace: 'nowrap',
              }}>
                快速
              </Typography>
            </Box>
            {/* 规划模式 */}
            <Box
              onClick={() => !isStreaming && setPlanMode(true)}
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.5,
                px: 1.5, py: 0.5,
                borderTopRightRadius: 'var(--radius-btn)',
                borderBottomRightRadius: 'var(--radius-btn)',
                border: '1px solid var(--border-subtle)',
                bgcolor: planMode ? 'rgba(212,160,83,0.12)' : 'transparent',
                cursor: isStreaming ? 'default' : 'pointer',
                opacity: isStreaming ? 0.5 : 1,
                transition: 'all 0.2s',
                position: 'relative',
                '&:hover': !isStreaming && !planMode ? { bgcolor: 'rgba(212,160,83,0.06)' } : {},
              }}
            >
              <MapIcon sx={{
                fontSize: 14,
                color: planMode ? 'var(--accent)' : 'var(--text-muted)',
              }} />
              <Typography sx={{
                fontSize: '0.72rem', fontWeight: planMode ? 700 : 500,
                color: planMode ? 'var(--accent)' : 'var(--text-muted)',
                fontFamily: 'var(--font-body)', whiteSpace: 'nowrap',
              }}>
                规划
              </Typography>
            </Box>
          </Box>

          {isStreaming && (
            <Chip
              label="回答中"
              size="small"
              sx={{
                ml: 1,
                bgcolor: 'rgba(45,212,191,0.1)',
                color: 'var(--accent-secondary)',
                fontWeight: 600, fontSize: '0.7rem',
                border: '1px solid rgba(45,212,191,0.2)',
              }}
              icon={
                <Box className="thinking-dots">
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
              }
            />
          )}

          {/* Plan Mode indicator banner */}
          {planMode && (
            <Chip
              label="规划模式"
              size="small"
              icon={<MapIcon sx={{ fontSize: 12 }} />}
              sx={{
                bgcolor: 'rgba(212,160,83,0.1)',
                color: 'var(--accent)',
                fontWeight: 600, fontSize: '0.68rem',
                border: '1px solid rgba(212,160,83,0.25)',
              }}
            />
          )}
        </Box>

        {/* Favorite repos quick-select */}
        <FavoriteReposBar onRepoSelect={(path) => setRepoPath(path)} />

        {/* Input area */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mb: 2 }}>
          <TextField
            fullWidth
            label="仓库路径"
            placeholder="E:/my-project"
            value={repoPath}
            onChange={(e) => setRepoPath(e.target.value)}
            disabled={isStreaming}
            size="small"
          />

          <Box sx={{ display: 'flex', gap: 0.8, flexWrap: 'wrap' }}>
            {QUICK_PROMPTS.map((qp) => (
              <Chip
                key={qp.label}
                label={qp.label}
                size="small"
                variant="outlined"
                onClick={() => handleQuickPrompt(qp)}
                disabled={isStreaming}
                sx={{
                  borderRadius: 1.5,
                  borderColor: 'rgba(212,160,83,0.2)',
                  color: 'var(--accent)',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  cursor: isStreaming ? 'default' : 'pointer',
                  transition: 'all 0.2s',
                  '&:hover': !isStreaming ? { bgcolor: 'rgba(212,160,83,0.08)', borderColor: 'var(--accent)' } : {},
                }}
              />
            ))}
          </Box>

          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              fullWidth
              label="输入问题"
              placeholder="请描述您的问题..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isStreaming}
              size="small"
              multiline
              maxRows={3}
            />
            {isStreaming ? (
              <Button
                variant="contained"
                color="error"
                onClick={stopStream}
                sx={{ minWidth: 52, borderRadius: 1.5 }}
              >
                <StopIcon fontSize="small" />
              </Button>
            ) : (
              <Button
                variant="contained"
                onClick={() => handleSend()}
                disabled={!question.trim() || !repoPath.trim()}
                className="gradient-btn"
                sx={{ minWidth: 52, p: '6px 10px !important' }}
              >
                <SendIcon fontSize="small" />
              </Button>
            )}
          </Box>
        </Box>

        {inputError && (
          <Fade in>
            <Alert severity="warning" sx={{ mb: 1.5, borderRadius: 2 }} onClose={() => setInputError(null)}>{inputError}</Alert>
          </Fade>
        )}
        {streamError && (
          <Fade in>
            <Alert severity="error" sx={{ mb: 1.5, borderRadius: 2 }}>{streamError}</Alert>
          </Fade>
        )}

        {/* Chat area */}
        <Box sx={{ position: 'relative', flex: 1, minHeight: 480, overflow: 'hidden' }}>
          <Paper
            ref={chatAreaRef}
            variant="outlined"
            className="custom-scrollbar"
            sx={{
              position: 'absolute', inset: 0,
              overflow: 'auto',
              p: 2, borderRadius: 2,
              bgcolor: 'rgba(0,0,0,0.15)',
              borderColor: 'var(--border-subtle)',
            }}
          >
            {/* Empty state */}
            {history.length === 0 && !isStreaming && (
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', minHeight: 200 }}>
                <Box
                  sx={{
                    width: 52, height: 52, borderRadius: 2, mb: 2,
                    background: 'rgba(45,212,191,0.08)',
                    border: '1px solid rgba(45,212,191,0.12)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <SmartToyIcon sx={{ color: 'var(--accent-secondary)', fontSize: 24 }} />
                </Box>
                <Typography variant="body2" sx={{ color: 'var(--text-muted)', mb: 0.5, fontWeight: 600 }}>
                  AI 代码助手已就绪
                </Typography>
                <Typography variant="caption" sx={{ color: 'var(--text-muted)', opacity: 0.6 }}>
                  点击上方快捷提问或输入问题开始对话
                </Typography>
              </Box>
            )}

            {/* Message history */}
            {history.map((msg, idx) => (
              <Box key={idx} sx={{ mb: 2.5, display: 'flex', flexDirection: msg.role === 'user' ? 'row-reverse' : 'row', gap: 1 }}>
                <Avatar sx={{
                  width: 28, height: 28, mt: 0.3, flexShrink: 0,
                  background: msg.role === 'user'
                    ? 'linear-gradient(135deg, var(--accent), var(--accent-hover))'
                    : 'linear-gradient(135deg, var(--accent-secondary), #0d9488)',
                  color: '#0c0b0e', fontSize: '0.7rem', fontWeight: 700,
                }}>
                  {msg.role === 'user'
                    ? 'You'
                    : <SmartToyIcon sx={{ fontSize: 16 }} />}
                </Avatar>

                <Box sx={{ maxWidth: '88%' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: msg.role === 'user' ? 'var(--accent)' : 'var(--accent-secondary)', fontSize: '0.7rem' }}>
                      {msg.role === 'user' ? '你' : 'AI 助手'}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'var(--text-muted)', fontSize: '0.65rem' }}>
                      {formatTime(msg.timestamp)}
                    </Typography>
                  </Box>

                  <div className={msg.role === 'user' ? 'chat-bubble-user' : 'chat-bubble-ai'}>
                    {msg.role === 'assistant' ? (
                      <MarkdownContent text={msg.content} darkMode={darkMode} />
                    ) : (
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.65, fontSize: '0.85rem', color: 'var(--text-primary)' }}>
                        {msg.content}
                      </Typography>
                    )}
                  </div>

                  {msg.role === 'assistant' && (
                    <Box sx={{ display: 'flex', gap: 0.5, mt: 0.3 }}>
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
              <Box sx={{ mb: 2.5, display: 'flex', gap: 1 }}>
                <Avatar sx={{
                  width: 28, height: 28, flexShrink: 0,
                  background: 'linear-gradient(135deg, var(--accent-secondary), #0d9488)',
                  color: '#0c0b0e',
                }}>
                  <SmartToyIcon sx={{ fontSize: 16 }} />
                </Avatar>
                <Box sx={{ maxWidth: '88%' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4 }}>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: 'var(--accent-secondary)', fontSize: '0.7rem' }}>AI 助手</Typography>
                    <Box className="thinking-dots" sx={{ display: 'inline-flex', gap: '3px' }}>
                      <Box component="span" className="dot" />
                      <Box component="span" className="dot" />
                      <Box component="span" className="dot" />
                    </Box>
                  </Box>
                  <div className="chat-bubble-ai">
                    <MarkdownContent text={streamingMessage} darkMode={darkMode} />
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
                <Box
                  sx={{
                    width: 28, height: 28, borderRadius: 1,
                    bgcolor: 'rgba(45,212,191,0.1)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <SmartToyIcon sx={{ fontSize: 16, color: 'var(--accent-secondary)' }} />
                </Box>
                <Box className="thinking-dots">
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
                <Typography variant="body2" sx={{ color: 'var(--text-muted)', ml: 0.5 }}>
                  思考中...
                </Typography>
              </Box>
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
          </Paper>

          {/* Scroll to bottom FAB */}
          <Zoom in={showScrollBtn}>
            <Box
              onClick={() => scrollToBottom()}
              sx={{
                position: 'absolute', bottom: 12, right: 12,
                width: 34, height: 34, borderRadius: '50%',
                bgcolor: 'var(--accent)',
                color: '#0c0b0e',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
                boxShadow: `0 4px 12px var(--accent-glow)`,
                transition: 'all 0.2s',
                '&:hover': { transform: 'scale(1.1)', boxShadow: `0 6px 16px var(--accent-glow)` },
                zIndex: 10,
              }}
            >
              <KeyboardArrowDownIcon sx={{ fontSize: 20 }} />
            </Box>
          </Zoom>
        </Box>
      </CardContent>
    </div>
  );
}
