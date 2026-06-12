import React, { useState, useEffect, useRef, createElement } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, Alert, LinearProgress, Chip,
  IconButton, Tooltip, Zoom,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import StopIcon from '@mui/icons-material/Stop';
import BugReportIcon from '@mui/icons-material/BugReport';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import { useSSE } from '../hooks/useSSE.js';
import { getReviewStreamUrl } from '../api/client.js';
import FavoriteReposBar from './FavoriteReposBar.jsx';

/* ─── Lightweight Markdown Renderer (shared with ChatPanel) ─── */
function MarkdownContent({ text }) {
  if (!text) return null;
  const segments = [];
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match;
  while ((match = codeBlockRegex.exec(text)) !== null) {
    if (match.index > lastIndex) segments.push({ type: 'text', content: text.slice(lastIndex, match.index) });
    segments.push({ type: 'code', lang: match[1] || 'plaintext', content: match[2].trimEnd() });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) segments.push({ type: 'text', content: text.slice(lastIndex) });

  function renderInline(content) {
    return content.split('\n').map((line, li) => {
      const hMatch = line.match(/^(#{1,3})\s+(.+)/);
      if (hMatch) {
        const sizes = ['1.05rem', '0.95rem', '0.87rem'];
        return createElement('div', { key: `h-${li}`, style: { fontWeight: 700, fontSize: sizes[hMatch[1].length - 1] || '0.87rem', marginTop: li > 0 ? 12 : 0, marginBottom: 4, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' } }, parseInline(hMatch[2]));
      }
      const ulMatch = line.match(/^[-*]\s+(.+)/);
      if (ulMatch) return createElement('div', { key: `ul-${li}`, style: { display: 'flex', gap: 8, paddingLeft: 4, marginTop: 2, marginBottom: 2 } }, createElement('span', { style: { color: 'var(--accent)', fontWeight: 700 } }, '·'), createElement('span', null, parseInline(ulMatch[1])));
      const olMatch = line.match(/^(\d+)\.\s+(.+)/);
      if (olMatch) return createElement('div', { key: `ol-${li}`, style: { display: 'flex', gap: 8, paddingLeft: 4, marginTop: 2, marginBottom: 2 } }, createElement('span', { style: { color: 'var(--accent)', fontWeight: 600, minWidth: 16 } }, `${olMatch[1]}.`), createElement('span', null, parseInline(olMatch[2])));
      if (/^[-*_]{3,}$/.test(line.trim())) return createElement('hr', { key: `hr-${li}`, style: { border: 'none', borderTop: '1px solid var(--border-subtle)', margin: '10px 0' } });
      if (line.trim() === '') return createElement('div', { key: `br-${li}`, style: { height: 8 } });
      return createElement('div', { key: `p-${li}` }, parseInline(line));
    });
  }

  function parseInline(str) {
    const tokens = [];
    const regex = /(\*\*(.+?)\*\*)|(\*(.+?)\*)|(`([^`]+)`)|(\[([^\]]+)\]\(([^)]+)\))/g;
    let last = 0, m;
    while ((m = regex.exec(str)) !== null) {
      if (m.index > last) tokens.push(createElement('span', { key: `t${last}` }, str.slice(last, m.index)));
      if (m[1] || m[3]) tokens.push(createElement('span', { key: `t${m.index}`, style: { fontWeight: m[1] ? 700 : 400, fontStyle: m[1] ? 'normal' : 'italic', color: 'var(--text-primary)' } }, m[2] || m[4]));
      else if (m[5]) tokens.push(createElement('code', { key: `t${m.index}`, style: { fontFamily: 'var(--font-code)', fontSize: '0.82em', padding: '2px 6px', borderRadius: 4, background: 'rgba(212,160,83,0.1)', color: 'var(--accent)' } }, m[6]));
      else if (m[7]) tokens.push(createElement('a', { key: `t${m.index}`, href: m[9], target: '_blank', rel: 'noopener', style: { color: 'var(--accent)', textDecoration: 'underline' } }, m[8]));
      last = m.index + m[0].length;
    }
    if (last < str.length) tokens.push(createElement('span', { key: `t${last}` }, str.slice(last)));
    return tokens.length === 0 ? str : tokens;
  }

  return createElement('div', { style: { lineHeight: 1.75 } },
    ...segments.map((seg, si) => {
      if (seg.type === 'code') {
        return createElement('div', { key: `seg${si}`, style: { margin: '8px 0', borderRadius: 8, overflow: 'hidden', border: '1px solid var(--border-subtle)' } },
          createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px', background: 'rgba(0,0,0,0.4)', fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'var(--font-code)' } },
            createElement('span', null, seg.lang),
            createElement('span', { style: { cursor: 'pointer', color: 'var(--accent)' }, onClick: (e) => { navigator.clipboard.writeText(seg.content); e.target.textContent = '已复制'; setTimeout(() => { e.target.textContent = '复制'; }, 1500); } }, '复制')
          ),
          createElement('pre', { style: { margin: 0, padding: '12px 16px', overflow: 'auto', maxHeight: 300, background: 'rgba(0,0,0,0.3)', fontFamily: 'var(--font-code)', fontSize: '0.79rem', lineHeight: 1.65, color: 'var(--text-primary)' } }, seg.content)
        );
      }
      return createElement('div', { key: `seg${si}`, style: { fontSize: '0.85rem', color: 'var(--text-secondary)' } }, renderInline(seg.content));
    })
  );
}

/* ========================================================
   ReviewPanel Component — Streaming Code Review
   ======================================================== */
export default function ReviewPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [error, setError] = useState(null);
  const [toolLog, setToolLog] = useState([]);
  const [copied, setCopied] = useState(false);
  const [showScrollBtn, setShowScrollBtn] = useState(false);

  const reviewAreaRef = useRef(null);
  const endRef = useRef(null);

  const {
    messages: streamingContent,
    isConnected,
    isLoading: streamLoading,
    error: streamError,
    startStream,
    stopStream,
  } = useSSE();

  const isStreaming = isConnected || streamLoading;

  // Auto-scroll
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [streamingContent, toolLog]);

  // Track tool calls from SSE events
  useEffect(() => {
    if (streamingContent) {
      const toolMatches = streamingContent.matchAll(/\[调用工具: (.+?)\]/g);
      const tools = [];
      for (const m of toolMatches) {
        if (!tools.includes(m[1])) tools.push(m[1]);
      }
      if (tools.length > 0) setToolLog(tools);
    }
  }, [streamingContent]);

  // Clean up streaming content: remove tool call markers for display
  const cleanContent = streamingContent
    ? streamingContent.replace(/\[调用工具: .+?\]/g, '').trim()
    : '';

  const handleReview = () => {
    if (!repoPath.trim()) { setError('请输入代码仓库路径'); return; }
    setError(null);
    setToolLog([]);
    setCopied(false);
    const url = getReviewStreamUrl(repoPath.trim());
    // useSSE sends { question, repoPath } as body; the review controller reads repoPath from body
    startStream('review', repoPath.trim(), url);
  };

  const handleCopy = () => {
    if (cleanContent) {
      navigator.clipboard.writeText(cleanContent).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  const handleScroll = (e) => {
    const el = e.target;
    setShowScrollBtn(el.scrollHeight - el.scrollTop - el.clientHeight > 80);
  };

  return (
    <div className="forge-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ p: '22px 24px !important', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2, mb: 2.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: 'rgba(212,160,83,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <BugReportIcon sx={{ color: 'var(--accent)', fontSize: 18 }} />
          </Box>
          <Typography className="section-title">代码审查</Typography>
          {isStreaming && (
            <Chip
              label="审查中"
              size="small"
              sx={{
                ml: 1, bgcolor: 'rgba(212,160,83,0.1)', color: 'var(--accent)',
                fontWeight: 600, fontSize: '0.7rem', border: '1px solid rgba(212,160,83,0.2)',
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
        </Box>

        {/* Favorite repos quick-select */}
        <FavoriteReposBar onRepoSelect={(path) => setRepoPath(path)} />

        {/* Input row */}
        <Box sx={{ display: 'flex', gap: 1.5, mb: 2 }}>
          <TextField
            fullWidth
            label="仓库路径 / GitHub URL"
            placeholder="E:/my-project 或 https://github.com/owner/repo"
            value={repoPath}
            onChange={(e) => setRepoPath(e.target.value)}
            disabled={isStreaming}
            size="small"
            sx={{ flex: 1 }}
            helperText={repoPath.trim().match(/github\.com/) ? '🌐 GitHub 仓库将通过 MCP 远程读取' : ''}
            FormHelperTextProps={{ sx: { fontSize: '0.68rem', color: 'var(--accent-secondary)', ml: 0.5 } }}
          />
          {isStreaming ? (
            <Button
              variant="contained"
              color="error"
              onClick={stopStream}
              sx={{ minWidth: 100, borderRadius: 1.5 }}
              startIcon={<StopIcon />}
            >
              停止
            </Button>
          ) : (
            <Button
              variant="contained"
              onClick={handleReview}
              disabled={!repoPath.trim()}
              className="gradient-btn"
              sx={{ minWidth: 130 }}
              startIcon={<SearchIcon />}
            >
              开始审查
            </Button>
          )}
        </Box>

        {/* Errors */}
        {(error || streamError) && (
          <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }} onClose={() => { setError(null); }}>{error || streamError}</Alert>
        )}

        {/* Loading bar */}
        {streamLoading && !isConnected && (
          <LinearProgress sx={{ mb: 2, borderRadius: 2, height: 3, bgcolor: 'rgba(212,160,83,0.1)', '& .MuiLinearProgress-bar': { bgcolor: 'var(--accent)' } }} />
        )}

        {/* Tool call indicators */}
        {toolLog.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.8, flexWrap: 'wrap', mb: 1.5 }}>
            {toolLog.map((tool, i) => (
              <Chip key={i} label={tool} size="small" variant="outlined"
                sx={{ borderRadius: 1.5, fontSize: '0.7rem', borderColor: 'rgba(45,212,191,0.3)', color: 'var(--accent-secondary)' }} />
            ))}
          </Box>
        )}

        {/* Review output area */}
        <Box sx={{ position: 'relative', flex: 1, minHeight: 400, overflow: 'hidden' }}>
          <Box
            ref={reviewAreaRef}
            onScroll={handleScroll}
            className="custom-scrollbar"
            sx={{
              position: 'absolute', inset: 0,
              overflow: 'auto', p: 2.5, borderRadius: 2,
              bgcolor: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-subtle)',
            }}
          >
            {/* Empty state */}
            {!isStreaming && !cleanContent && (
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', minHeight: 200 }}>
                <Box sx={{
                  width: 52, height: 52, borderRadius: 2, mb: 2,
                  background: 'rgba(212,160,83,0.06)', border: '1px solid rgba(212,160,83,0.1)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <BugReportIcon sx={{ fontSize: 24, color: 'var(--text-muted)' }} />
                </Box>
                <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)', mb: 0.5, fontWeight: 600 }}>
                  输入仓库路径，点击「开始审查」
                </Typography>
                <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)', opacity: 0.6 }}>
                  支持本地路径和 GitHub URL，审查结果实时流式输出
                </Typography>
              </Box>
            )}

            {/* Streaming / completed content */}
            {cleanContent && (
              <Box>
                <MarkdownContent text={cleanContent} />
                {isStreaming && (
                  <Box component="span" sx={{
                    display: 'inline-block', width: 7, height: 15,
                    bgcolor: 'var(--accent)', ml: 0.3, borderRadius: 1.5,
                    animation: 'cursorBlink 1s step-end infinite', verticalAlign: 'text-bottom',
                  }} />
                )}
              </Box>
            )}

            {/* Thinking state */}
            {streamLoading && !isConnected && !cleanContent && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 3 }}>
                <Box sx={{
                  width: 28, height: 28, borderRadius: 1,
                  bgcolor: 'rgba(212,160,83,0.1)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <BugReportIcon sx={{ fontSize: 16, color: 'var(--accent)' }} />
                </Box>
                <Box className="thinking-dots">
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                  <Box component="span" className="dot" />
                </Box>
                <Typography sx={{ color: 'var(--text-muted)', ml: 0.5 }}>正在分析仓库...</Typography>
              </Box>
            )}

            <div ref={endRef} />
          </Box>

          {/* Scroll to bottom FAB */}
          <Zoom in={showScrollBtn}>
            <Box
              onClick={() => endRef.current?.scrollIntoView({ behavior: 'smooth' })}
              sx={{
                position: 'absolute', bottom: 12, right: 12,
                width: 34, height: 34, borderRadius: '50%',
                bgcolor: 'var(--accent)', color: '#0c0b0e',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer', boxShadow: `0 4px 12px var(--accent-glow)`,
                transition: 'all 0.2s',
                '&:hover': { transform: 'scale(1.1)' },
                zIndex: 10,
              }}
            >
              <KeyboardArrowDownIcon sx={{ fontSize: 20 }} />
            </Box>
          </Zoom>
        </Box>

        {/* Action bar (shown after review completes) */}
        {cleanContent && !isStreaming && (
          <Box sx={{ display: 'flex', gap: 1, mt: 1.5, justifyContent: 'flex-end' }}>
            <Tooltip title="复制审查结果" arrow>
              <IconButton size="small" onClick={handleCopy}
                sx={{ color: copied ? 'var(--success)' : 'var(--text-muted)', '&:hover': { color: 'var(--accent)' } }}>
                {copied ? <CheckIcon sx={{ fontSize: 16 }} /> : <ContentCopyIcon sx={{ fontSize: 16 }} />}
              </IconButton>
            </Tooltip>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
