import React, { useState, useRef, useEffect, useCallback, createElement } from 'react';
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
import { useSSE } from '../hooks/useSSE.js';

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

  // 1) Split by fenced code blocks (```...```)
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

  // 2) Render inline markdown within text segments
  function renderInline(content) {
    const lines = content.split('\n');
    return lines.map((line, li) => {
      // Headings
      const hMatch = line.match(/^(#{1,3})\s+(.+)/);
      if (hMatch) {
        const level = hMatch[1].length;
        const sizes = ['1.05rem', '0.95rem', '0.87rem'];
        return createElement('div', {
          key: `h-${li}`,
          style: { fontWeight: 700, fontSize: sizes[level - 1] || '0.87rem', marginTop: li > 0 ? 12 : 0, marginBottom: 4, color: darkMode ? '#e2e8f0' : '#1e293b' },
        }, parseInlineTokens(hMatch[2]));
      }

      // Unordered list
      const ulMatch = line.match(/^[-*]\s+(.+)/);
      if (ulMatch) {
        return createElement('div', {
          key: `ul-${li}`,
          style: { display: 'flex', gap: 8, marginTop: 2, marginBottom: 2, paddingLeft: 4 },
        },
          createElement('span', { style: { color: '#6366f1', flexShrink: 0, fontWeight: 700 } }, '·'),
          createElement('span', null, parseInlineTokens(ulMatch[1]))
        );
      }

      // Ordered list
      const olMatch = line.match(/^(\d+)\.\s+(.+)/);
      if (olMatch) {
        return createElement('div', {
          key: `ol-${li}`,
          style: { display: 'flex', gap: 8, marginTop: 2, marginBottom: 2, paddingLeft: 4 },
        },
          createElement('span', { style: { color: '#6366f1', flexShrink: 0, fontWeight: 600, minWidth: 16 } }, `${olMatch[1]}.`),
          createElement('span', null, parseInlineTokens(olMatch[2]))
        );
      }

      // Horizontal rule
      if (/^[-*_]{3,}$/.test(line.trim())) {
        return createElement('hr', { key: `hr-${li}`, style: { border: 'none', borderTop: `1px solid ${darkMode ? '#334155' : '#e2e8f0'}`, margin: '10px 0' } });
      }

      // Empty line → paragraph break
      if (line.trim() === '') {
        return createElement('div', { key: `br-${li}`, style: { height: 8 } });
      }

      return createElement('div', { key: `p-${li}`, style: { marginTop: li > 0 && lines[li - 1].trim() !== '' ? 0 : 0 } }, parseInlineTokens(line));
    });
  }

  // Parse bold, italic, inline code, links
  function parseInlineTokens(str) {
    const tokens = [];
    const regex = /(\*\*(.+?)\*\*)|(\*(.+?)\*)|(`([^`]+)`)|(\[([^\]]+)\]\(([^)]+)\))/g;
    let last = 0;
    let m;
    while ((m = regex.exec(str)) !== null) {
      if (m.index > last) tokens.push(createElement('span', { key: `t${last}` }, str.slice(last, m.index)));
      if (m[1] || m[3]) {
        // Bold or italic
        const isBold = !!m[1];
        tokens.push(createElement('span', {
          key: `t${m.index}`,
          style: { fontWeight: isBold ? 700 : 400, fontStyle: isBold ? 'normal' : 'italic', color: darkMode ? '#e2e8f0' : '#1e293b' },
        }, m[2] || m[4]));
      } else if (m[5]) {
        // Inline code
        tokens.push(createElement('code', {
          key: `t${m.index}`,
          style: {
            fontFamily: '"JetBrains Mono", monospace', fontSize: '0.82em', padding: '2px 6px', borderRadius: 4,
            background: darkMode ? 'rgba(99,102,241,0.15)' : 'rgba(99,102,241,0.08)', color: '#6366f1',
          },
        }, m[6]));
      } else if (m[7]) {
        // Link
        tokens.push(createElement('a', {
          key: `t${m.index}`,
          href: m[9],
          target: '_blank',
          rel: 'noopener',
          style: { color: '#6366f1', textDecoration: 'underline', fontWeight: 500 },
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
        return createElement('div', { key: `seg${si}`, style: { margin: '8px 0', borderRadius: 8, overflow: 'hidden', border: `1px solid ${darkMode ? '#1e293b' : '#e2e8f0'}` } },
          createElement('div', {
            style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 12px', background: darkMode ? '#0f172a' : '#1e293b', fontSize: '0.7rem', color: '#94a3b8', fontFamily: '"JetBrains Mono", monospace' },
          },
            createElement('span', null, seg.lang),
            createElement('span', { style: { cursor: 'pointer', color: '#6366f1' }, onClick: (e) => { navigator.clipboard.writeText(seg.content); e.target.textContent = '已复制'; setTimeout(() => { e.target.textContent = '复制'; }, 1500); } }, '复制')
          ),
          createElement('pre', {
            style: { margin: 0, padding: '12px 16px', overflow: 'auto', maxHeight: 300, background: darkMode ? '#0a0a1a' : '#0f172a', fontFamily: '"JetBrains Mono", "Fira Code", monospace', fontSize: '0.79rem', lineHeight: 1.65, color: '#e2e8f0' },
          }, seg.content)
        );
      }
      return createElement('div', { key: `seg${si}`, style: { fontSize: '0.85rem', color: darkMode ? '#cbd5e1' : '#334155' } }, renderInline(seg.content));
    })
  );
}

/* ========================================================
   ChatPanel Component
   ======================================================== */
export default function ChatPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [question, setQuestion] = useState('');
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
    startStream,
    stopStream,
  } = useSSE();

  // Scroll handling
  const scrollToBottom = useCallback((smooth = true) => {
    messagesEndRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto' });
  }, []);

  // Auto-scroll when new content arrives
  useEffect(() => {
    scrollToBottom();
  }, [streamingMessage, history, scrollToBottom]);

  // Detect if user scrolled up
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

  // Send message
  const handleSend = useCallback((text) => {
    const msg = (text || question).trim();
    if (!repoPath.trim()) { setInputError('请输入代码仓库路径'); return; }
    if (!msg) { setInputError('请输入您的问题'); return; }
    setInputError(null);
    setHistory((prev) => [...prev, { role: 'user', content: msg, timestamp: Date.now() }]);
    startStream(msg, repoPath.trim());
    setQuestion('');
  }, [repoPath, question, startStream]);

  // Quick prompt click
  const handleQuickPrompt = useCallback((prompt) => {
    if (isConnected || streamLoading) return;
    setQuestion(prompt.text);
    setTimeout(() => handleSend(prompt.text), 50);
  }, [isConnected, streamLoading, handleSend]);

  // Save assistant message to history when stream ends
  useEffect(() => {
    if (!isConnected && !streamLoading && streamingMessage) {
      setHistory((prev) => [...prev, { role: 'assistant', content: streamingMessage, timestamp: Date.now() }]);
    }
  }, [isConnected, streamLoading, streamingMessage]);

  // Copy message
  const handleCopyMsg = (content, idx) => {
    navigator.clipboard.writeText(content).then(() => {
      setCopiedIdx(idx);
      setTimeout(() => setCopiedIdx(null), 2000);
    }).catch(() => {});
  };

  // Regenerate (re-ask last user question)
  const handleRegenerate = useCallback(() => {
    if (isConnected || streamLoading) return;
    const lastUserMsg = [...history].reverse().find(m => m.role === 'user');
    if (lastUserMsg) {
      startStream(lastUserMsg.content, repoPath.trim());
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
    <div className="glass-card" style={{ height: '100%' }}>
      <CardContent sx={{ p: '20px 24px !important', display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <PsychologyIcon sx={{ color: '#06b6d4', fontSize: 22 }} />
          <Typography className="section-title">智能问答</Typography>
          {isStreaming && (
            <Chip
              label="回答中"
              size="small"
              sx={{ ml: 1, bgcolor: 'rgba(6,182,212,0.1)', color: '#06b6d4', fontWeight: 600, fontSize: '0.7rem' }}
              icon={
                <Box className="thinking-dots">
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
              }
            />
          )}
        </Box>

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

          {/* Quick Prompts */}
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
                  borderRadius: 8,
                  borderColor: 'rgba(99,102,241,0.3)',
                  color: '#6366f1',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                  cursor: isStreaming ? 'default' : 'pointer',
                  '&:hover': !isStreaming ? { bgcolor: 'rgba(99,102,241,0.08)', borderColor: '#6366f1' } : {},
                  transition: 'all 0.2s',
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
        <Box sx={{ position: 'relative', flex: 1 }}>
          <Paper
            ref={chatAreaRef}
            variant="outlined"
            className="custom-scrollbar"
            sx={{
              position: 'absolute', inset: 0,
              overflow: 'auto',
              p: 2, borderRadius: 2,
              bgcolor: darkMode ? 'rgba(0,0,0,0.15)' : 'rgba(248,250,252,0.6)',
            }}
          >
            {/* Empty state */}
            {history.length === 0 && !isStreaming && (
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', minHeight: 200 }}>
                <Box
                  sx={{
                    width: 56, height: 56, borderRadius: 2, mb: 2,
                    background: 'linear-gradient(135deg, rgba(6,182,212,0.15), rgba(99,102,241,0.15))',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <SmartToyIcon sx={{ color: '#06b6d4', fontSize: 28 }} />
                </Box>
                <Typography variant="body2" sx={{ color: darkMode ? '#64748b' : '#94a3b8', mb: 0.5, fontWeight: 600 }}>
                  AI 代码助手已就绪
                </Typography>
                <Typography variant="caption" sx={{ color: darkMode ? '#475569' : '#cbd5e1' }}>
                  点击上方快捷提问或输入问题开始对话
                </Typography>
              </Box>
            )}

            {/* Message history */}
            {history.map((msg, idx) => (
              <Box key={idx} sx={{ mb: 2.5, display: 'flex', flexDirection: msg.role === 'user' ? 'row-reverse' : 'row', gap: 1 }}>
                <Avatar sx={{ width: 28, height: 28, mt: 0.3, flexShrink: 0, bgcolor: msg.role === 'user' ? '#6366f1' : '#22c55e', fontSize: '0.8rem' }}>
                  {msg.role === 'user' ? 'You' : <SmartToyIcon sx={{ fontSize: 16 }} />}
                </Avatar>

                <Box sx={{ maxWidth: '88%' }}>
                  {/* Sender name & time */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4, flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }}>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: msg.role === 'user' ? '#6366f1' : '#22c55e', fontSize: '0.7rem' }}>
                      {msg.role === 'user' ? '你' : 'AI 助手'}
                    </Typography>
                    <Typography variant="caption" sx={{ color: darkMode ? '#475569' : '#94a3b8', fontSize: '0.65rem' }}>
                      {formatTime(msg.timestamp)}
                    </Typography>
                  </Box>

                  {/* Bubble */}
                  <div className={msg.role === 'user' ? 'chat-bubble-user' : 'chat-bubble-ai'}>
                    {msg.role === 'assistant' ? (
                      <MarkdownContent text={msg.content} darkMode={darkMode} />
                    ) : (
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.65, fontSize: '0.85rem' }}>
                        {msg.content}
                      </Typography>
                    )}
                  </div>

                  {/* AI message actions */}
                  {msg.role === 'assistant' && (
                    <Box sx={{ display: 'flex', gap: 0.5, mt: 0.3 }}>
                      <Tooltip title="复制" arrow>
                        <IconButton
                          size="small"
                          onClick={() => handleCopyMsg(msg.content, idx)}
                          sx={{ color: copiedIdx === idx ? '#22c55e' : (darkMode ? '#64748b' : '#94a3b8'), '&:hover': { color: '#6366f1' } }}
                        >
                          {copiedIdx === idx ? <CheckIcon sx={{ fontSize: 14 }} /> : <ContentCopyIcon sx={{ fontSize: 14 }} />}
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="重新生成" arrow>
                        <IconButton
                          size="small"
                          onClick={handleRegenerate}
                          disabled={isStreaming}
                          sx={{ color: darkMode ? '#64748b' : '#94a3b8', '&:hover': { color: '#6366f1' } }}
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
                <Avatar sx={{ width: 28, height: 28, flexShrink: 0, bgcolor: '#22c55e', fontSize: '0.8rem' }}>
                  <SmartToyIcon sx={{ fontSize: 16 }} />
                </Avatar>
                <Box sx={{ maxWidth: '88%' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4 }}>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: '#22c55e', fontSize: '0.7rem' }}>AI 助手</Typography>
                    <Box className="thinking-dots" sx={{ display: 'inline-flex', gap: '3px' }}>
                      <Box component="span" className="dot" />
                      <Box component="span" className="dot" />
                      <Box component="span" className="dot" />
                    </Box>
                  </Box>
                  <div className="chat-bubble-ai">
                    <MarkdownContent text={streamingMessage} darkMode={darkMode} />
                    <Box component="span" sx={{ display: 'inline-block', width: 8, height: 16, bgcolor: '#6366f1', ml: 0.3, borderRadius: 2, animation: 'blink 1s step-end infinite', verticalAlign: 'text-bottom' }} />
                  </div>
                </Box>
              </Box>
            )}

            {/* Thinking state */}
            {streamLoading && !streamingMessage && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 2, ml: 1 }}>
                <Box
                  sx={{
                    width: 28, height: 28, borderRadius: 1, bgcolor: 'rgba(6,182,212,0.1)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <SmartToyIcon sx={{ fontSize: 16, color: '#06b6d4' }} />
                </Box>
                <Box className="thinking-dots">
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
                <Typography variant="body2" sx={{ color: darkMode ? '#64748b' : '#94a3b8', ml: 0.5 }}>
                  思考中...
                </Typography>
              </Box>
            )}

            <div ref={messagesEndRef} />
          </Paper>

          {/* Scroll to bottom FAB */}
          <Zoom in={showScrollBtn}>
            <Box
              onClick={() => scrollToBottom()}
              sx={{
                position: 'absolute', bottom: 12, right: 12,
                width: 36, height: 36, borderRadius: '50%',
                bgcolor: darkMode ? 'rgba(99,102,241,0.8)' : 'rgba(99,102,241,0.9)',
                color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
                boxShadow: '0 4px 12px rgba(99,102,241,0.4)',
                backdropFilter: 'blur(8px)',
                transition: 'all 0.2s',
                '&:hover': { transform: 'scale(1.1)', boxShadow: '0 6px 16px rgba(99,102,241,0.6)' },
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
