import React, { useState, useEffect, useRef, useCallback, createElement } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, Alert, LinearProgress, Chip,
  IconButton, Tooltip, Zoom, Fade, Paper, Collapse, Divider,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import StopIcon from '@mui/icons-material/Stop';
import BugReportIcon from '@mui/icons-material/BugReport';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import PsychologyIcon from '@mui/icons-material/Psychology';
import RateReviewIcon from '@mui/icons-material/RateReview';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import WifiOffIcon from '@mui/icons-material/WifiOff';
import SignalWifiStatusbar4BarIcon from '@mui/icons-material/SignalWifiStatusbar4Bar';
import { useSSE } from '../hooks/useSSE.js';
import { getReviewStreamUrl, getPreference } from '../api/client.js';
import FavoriteReposBar from './FavoriteReposBar.jsx';
import AgentTimeline from './AgentTimeline.jsx';

/* ═══════════════════════════════════════════════
   Lightweight Markdown Renderer
   ═══════════════════════════════════════════════ */
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

/* ═══════════════════════════════════════════════
   Phase Configuration
   ═══════════════════════════════════════════════ */
const PHASE_CONFIG = {
  idle:       { label: '准备就绪', icon: BugReportIcon,         desc: '输入仓库路径，开始 AI 代码审查' },
  connecting: { label: '正在连接', icon: SignalWifiStatusbar4BarIcon, desc: '正在连接 AI 服务...' },
  scanning:   { label: '扫描仓库', icon: FolderOpenIcon,        desc: '正在扫描仓库结构与文件...' },
  analyzing:  { label: '分析代码', icon: PsychologyIcon,        desc: 'AI 正在逐文件分析代码质量...' },
  reporting:  { label: '生成报告', icon: RateReviewIcon,        desc: '正在生成审查报告...' },
  complete:   { label: '审查完成', icon: TaskAltIcon,            desc: '审查已结束' },
};

const PHASE_ORDER = ['idle', 'connecting', 'scanning', 'analyzing', 'reporting', 'complete'];

const SEVERITY_CONFIG = {
  error:      { label: '严重',  color: '#ef4444', bg: 'rgba(239,68,68,0.12)',   border: 'rgba(239,68,68,0.3)',   icon: '\u{1F534}' },
  warning:    { label: '警告',  color: '#f59e0b', bg: 'rgba(245,158,11,0.12)',  border: 'rgba(245,158,11,0.3)',  icon: '\u{1F7E1}' },
  info:       { label: '建议',  color: '#3b82f6', bg: 'rgba(59,130,246,0.12)',  border: 'rgba(59,130,246,0.3)',  icon: '\u{1F535}' },
  suggestion: { label: '优化',  color: '#22c55e', bg: 'rgba(34,197,94,0.12)',   border: 'rgba(34,197,94,0.3)',   icon: '\u{1F7E2}' },
};

/* ═══════════════════════════════════════════════
   PhaseIndicator — Inline progress stepper
   ═══════════════════════════════════════════════ */
function PhaseIndicator({ phase, darkMode }) {
  if (phase === 'idle' || phase === 'complete') return null;

  const currentIdx = PHASE_ORDER.indexOf(phase);
  const relevantPhases = ['scanning', 'analyzing', 'reporting'];

  return (
    <Fade in timeout={400}>
      <Paper
        variant="outlined"
        sx={{
          mb: 1.5, borderRadius: 2,
          border: '1px solid var(--border-subtle)',
          bgcolor: 'rgba(0,0,0,0.12)',
          px: 2, py: 1.5,
        }}
      >
        {/* Active phase description */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.2 }}>
          <Box sx={{
            width: 28, height: 28, borderRadius: 1,
            bgcolor: 'rgba(212,160,83,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <BugReportIcon sx={{ fontSize: 15, color: 'var(--accent)' }} />
          </Box>
          <Typography sx={{ fontSize: '0.82rem', color: 'var(--text-primary)', fontWeight: 600 }}>
            {PHASE_CONFIG[phase]?.desc || '处理中...'}
          </Typography>
          <Box className="thinking-dots" sx={{ display: 'flex', gap: '3px', ml: 0.5 }}>
            <Box component="span" className="dot" />
            <Box component="span" className="dot" />
            <Box component="span" className="dot" />
          </Box>
        </Box>

        {/* Step dots */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
          {relevantPhases.map((p, i) => {
            const pIdx = PHASE_ORDER.indexOf(p);
            const isActive = pIdx === currentIdx;
            const isDone = pIdx < currentIdx;
            const isPending = pIdx > currentIdx;
            const config = PHASE_CONFIG[p];

            return (
              <React.Fragment key={p}>
                {i > 0 && (
                  <Box sx={{
                    flex: 1, height: 2, borderRadius: 1, minWidth: 24,
                    bgcolor: isDone ? 'var(--accent)' : 'var(--border-subtle)',
                    transition: 'background 0.5s',
                  }} />
                )}
                <Box sx={{
                  display: 'flex', alignItems: 'center', gap: 0.5,
                  opacity: isPending ? 0.4 : 1,
                  transition: 'opacity 0.3s',
                }}>
                  <Box sx={{
                    width: 20, height: 20, borderRadius: '50%',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: isActive
                      ? 'var(--accent)'
                      : isDone
                        ? 'rgba(212,160,83,0.15)'
                        : 'rgba(255,255,255,0.05)',
                    border: isActive
                      ? '2px solid var(--accent)'
                      : isDone
                        ? '2px solid rgba(212,160,83,0.3)'
                        : '1px solid var(--border-subtle)',
                    animation: isActive ? 'phasePulse 2s ease-in-out infinite' : 'none',
                    '@keyframes phasePulse': {
                      '0%, 100%': { boxShadow: '0 0 0 0 rgba(212,160,83,0.4)' },
                      '50%': { boxShadow: '0 0 0 6px rgba(212,160,83,0)' },
                    },
                  }}>
                    {isDone ? (
                      <CheckIcon sx={{ fontSize: 12, color: 'var(--accent)' }} />
                    ) : (
                      createElement(config.icon, { style: { fontSize: 11, color: isActive ? '#0c0b0e' : 'var(--text-muted)' } })
                    )}
                  </Box>
                  <Typography sx={{
                    fontSize: '0.68rem', fontWeight: isActive ? 600 : 400,
                    color: isActive ? 'var(--text-primary)' : 'var(--text-muted)',
                    whiteSpace: 'nowrap',
                  }}>
                    {config.label}
                  </Typography>
                </Box>
              </React.Fragment>
            );
          })}
        </Box>
      </Paper>
    </Fade>
  );
}

/* ═══════════════════════════════════════════════
   ReviewPanel Component — Streaming Code Review
   ═══════════════════════════════════════════════ */
export default function ReviewPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [localError, setLocalError] = useState(null);
  const [toolLog, setToolLog] = useState([]);
  const [copied, setCopied] = useState(false);
  const [showScrollBtn, setShowScrollBtn] = useState(false);
  const [isUserScrolledUp, setIsUserScrolledUp] = useState(false);
  const [newContentWhileScrolledUp, setNewContentWhileScrolledUp] = useState(false);
  const [preferences, setPreferences] = useState(null);

  const reviewAreaRef = useRef(null);
  const endRef = useRef(null);
  const lastContentLenRef = useRef(0);

  const {
    messages: streamingContent,
    isConnected,
    isLoading: streamLoading,
    error: streamError,
    errorType,
    phase,
    toolCallCount,
    traceEvents,
    summary,
    startStream,
    stopStream,
  } = useSSE();

  const [timelineOpen, setTimelineOpen] = useState(false);

  // Load user preferences on mount
  useEffect(() => {
    getPreference()
      .then((data) => setPreferences(data))
      .catch(() => {}); // silently fail if not logged in
  }, []);

  // Auto-open timeline when first trace event arrives
  useEffect(() => {
    if (traceEvents.length > 0 && !timelineOpen) {
      setTimelineOpen(true);
    }
  }, [traceEvents.length]);

  // Reset local state when stream starts
  useEffect(() => {
    if (streamLoading) {
      setToolLog([]);
      setCopied(false);
      setIsUserScrolledUp(false);
      setNewContentWhileScrolledUp(false);
      lastContentLenRef.current = 0;
    }
  }, [streamLoading]);

  const isStreaming = isConnected || streamLoading;

  /* ─── Smart auto-scroll ─── */
  const scrollToBottom = useCallback((force = false) => {
    if (!isUserScrolledUp || force) {
      requestAnimationFrame(() => {
        endRef.current?.scrollIntoView({ behavior: force ? 'auto' : 'smooth' });
      });
    }
  }, [isUserScrolledUp]);

  // Auto-scroll when new content arrives (but respect user scroll position)
  useEffect(() => {
    const currentLen = streamingContent?.length || 0;
    if (currentLen > lastContentLenRef.current) {
      if (isUserScrolledUp) {
        setNewContentWhileScrolledUp(true);
      } else {
        scrollToBottom(false);
      }
    }
    lastContentLenRef.current = currentLen;
  }, [streamingContent, isUserScrolledUp, scrollToBottom]);

  // Handle user manually scrolling to bottom
  const handleScrollAreaScroll = useCallback((e) => {
    const el = e.target;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const isBottom = distanceFromBottom < 50;
    setIsUserScrolledUp(!isBottom);
    setShowScrollBtn(distanceFromBottom > 80);
    if (isBottom) {
      setNewContentWhileScrolledUp(false);
    }
  }, []);

  // Track tool calls from SSE content markers
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
    if (!repoPath.trim()) { setLocalError('请输入代码仓库路径'); return; }
    setLocalError(null);
    const url = getReviewStreamUrl(repoPath.trim());
    startStream('review', repoPath.trim(), url);
  };

  const handleCopy = useCallback(() => {
    if (cleanContent) {
      navigator.clipboard.writeText(cleanContent).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  }, [cleanContent]);

  // Render categorized error
  const renderError = () => {
    const err = streamError || localError;
    if (!err) return null;

    const et = errorType || {};
    const severityColor = et.type === 'auth' ? '#f59e0b'
      : et.type === 'quota' ? '#f59e0b'
      : et.type === 'timeout' ? '#f59e0b'
      : '#ef4444';

    return (
      <Fade in timeout={300}>
        <Alert
          severity={et.type === 'auth' || et.type === 'quota' ? 'warning' : 'error'}
          icon={et.type ? createElement('span', { style: { fontSize: '1.1rem' } }, et.icon) : <ErrorOutlineIcon />}
          sx={{
            mb: 2, borderRadius: 2,
            bgcolor: `${severityColor}10`,
            border: `1px solid ${severityColor}30`,
            '& .MuiAlert-message': { flex: 1 },
            '& .MuiAlert-icon': { alignItems: 'center', color: severityColor },
          }}
          onClose={() => { setLocalError(null); }}
        >
          <Typography sx={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--text-primary)' }}>
            {et.title || '发生错误'}
          </Typography>
          <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-secondary)', mt: 0.3 }}>
            {et.hint || err}
          </Typography>
        </Alert>
      </Fade>
    );
  };

  /* ─── Preference chips ─── */
  const hasActivePrefs = preferences && (preferences.reviewFocus || preferences.reviewDepth);

  /* ─── Summary grouped by severity ─── */
  const severityCounts = summary && Array.isArray(summary)
    ? summary.reduce((acc, item) => {
        const sev = item.severity || item.level || 'info';
        acc[sev] = (acc[sev] || 0) + 1;
        return acc;
      }, {})
    : null;

  const totalIssues = severityCounts
    ? Object.values(severityCounts).reduce((sum, c) => sum + c, 0)
    : 0;

  // Handle severity chip click → scroll to matching section in text
  const handleSeverityFilter = (sev) => {
    const area = reviewAreaRef.current;
    if (!area || !cleanContent) return;
    const keywordMap = {
      error: ['严重', 'Error', 'error', '漏洞', '缺陷'],
      warning: ['警告', 'Warning', 'warning', '风险', '潜在'],
      info: ['建议', 'Info', 'info', '注意', '提示'],
      suggestion: ['优化', 'Suggestion', 'suggestion', '改进'],
    };
    const keywords = keywordMap[sev] || [sev];
    const lower = cleanContent.toLowerCase();
    let idx = -1;
    for (const kw of keywords) {
      idx = lower.indexOf(kw.toLowerCase());
      if (idx >= 0) break;
    }
    if (idx >= 0) {
      // Find the corresponding text position and scroll
      const textBeforeMatch = cleanContent.slice(0, idx);
      const lineCount = textBeforeMatch.split('\n').length;
      const approxScrollPos = lineCount * 24; // rough line height
      area.scrollTo({ top: approxScrollPos - 60, behavior: 'smooth' });
    }
  };

  return (
    <div className="forge-card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ p: '22px 24px !important', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
        {/* ── Header ── */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2, mb: 2 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: isStreaming ? 'rgba(212,160,83,0.15)' : 'rgba(212,160,83,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'background 0.3s',
          }}>
            <BugReportIcon sx={{ color: 'var(--accent)', fontSize: 18 }} />
          </Box>
          <Typography className="section-title">代码审查</Typography>

          {/* Phase badge */}
          {isStreaming && phase !== 'idle' && (
            <Chip
              label={PHASE_CONFIG[phase]?.label || '处理中'}
              size="small"
              sx={{
                ml: 0.5, bgcolor: 'rgba(212,160,83,0.1)', color: 'var(--accent)',
                fontWeight: 600, fontSize: '0.7rem', border: '1px solid rgba(212,160,83,0.2)',
              }}
            />
          )}

          {/* Complete badge */}
          {!isStreaming && cleanContent && (
            <Chip
              label="已完成"
              size="small"
              sx={{
                ml: 0.5, bgcolor: 'rgba(34,197,94,0.1)', color: '#22c55e',
                fontWeight: 600, fontSize: '0.7rem', border: '1px solid rgba(34,197,94,0.2)',
              }}
              icon={<CheckIcon sx={{ fontSize: 12 }} />}
            />
          )}
        </Box>

        {/* ── Favorite repos quick-select ── */}
        <FavoriteReposBar onRepoSelect={(path) => setRepoPath(path)} />

        {/* ── Input row ── */}
        <Box sx={{ display: 'flex', gap: 1.5, mb: 1.5 }}>
          <TextField
            fullWidth
            label="仓库路径 / GitHub URL"
            placeholder="E:/my-project 或 https://github.com/owner/repo"
            value={repoPath}
            onChange={(e) => { setRepoPath(e.target.value); setLocalError(null); }}
            disabled={isStreaming}
            size="small"
            sx={{ flex: 1 }}
            helperText={repoPath.trim().match(/github\.com/) ? '🌐 GitHub 仓库将通过 MCP 远程读取' : ''}
            FormHelperTextProps={{ sx: { fontSize: '0.68rem', color: 'var(--accent-secondary)', ml: 0.5 } }}
            onKeyDown={(e) => { if (e.key === 'Enter' && !isStreaming && repoPath.trim()) handleReview(); }}
          />
          {isStreaming ? (
            <Button
              variant="contained"
              color="error"
              onClick={stopStream}
              sx={{ minWidth: 100, borderRadius: 1.5 }}
              startIcon={<StopIcon />}
            >
              停止审查
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

        {/* ── Active preference chips ── */}
        {hasActivePrefs && !isStreaming && !cleanContent && (
          <Fade in timeout={400}>
            <Box sx={{ display: 'flex', gap: 0.6, flexWrap: 'wrap', mb: 1.5 }}>
              {preferences.reviewFocus && (
                <Chip
                  label={`关注: ${preferences.reviewFocus}`}
                  size="small"
                  variant="outlined"
                  sx={{ borderRadius: 1.5, fontSize: '0.68rem', borderColor: 'rgba(45,212,191,0.25)', color: 'var(--accent-secondary)', height: 22 }}
                />
              )}
              {preferences.reviewDepth && (
                <Chip
                  label={`深度: ${preferences.reviewDepth}`}
                  size="small"
                  variant="outlined"
                  sx={{ borderRadius: 1.5, fontSize: '0.68rem', borderColor: 'rgba(45,212,191,0.25)', color: 'var(--accent-secondary)', height: 22 }}
                />
              )}
              {preferences.customPrompt && (
                <Chip
                  label="自定义指令"
                  size="small"
                  variant="outlined"
                  sx={{ borderRadius: 1.5, fontSize: '0.68rem', borderColor: 'rgba(212,160,83,0.25)', color: 'var(--accent)', height: 22 }}
                />
              )}
            </Box>
          </Fade>
        )}

        {/* ── Errors ── */}
        {renderError()}

        {/* ── Phase progress indicator ── */}
        <PhaseIndicator phase={phase} darkMode={darkMode} />

        {/* ── Loading bar (connecting phase) ── */}
        {phase === 'connecting' && (
          <LinearProgress sx={{
            mb: 2, borderRadius: 2, height: 3,
            bgcolor: 'rgba(212,160,83,0.08)',
            '& .MuiLinearProgress-bar': { bgcolor: 'var(--accent)', transition: 'transform 0.3s linear' },
          }} />
        )}

        {/* ── Summary severity chips (interactive) ── */}
        {severityCounts && !isStreaming && (
          <Fade in timeout={500}>
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, flexWrap: 'wrap', mb: 1.5 }}>
                <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600, mr: 0.5 }}>
                  审查结果 ({totalIssues})
                </Typography>
                {Object.entries(severityCounts).map(([sev, count]) => {
                  const cfg = SEVERITY_CONFIG[sev] || SEVERITY_CONFIG.info;
                  return (
                    <Chip
                      key={sev}
                      label={`${cfg.icon} ${cfg.label}: ${count}`}
                      size="small"
                      onClick={() => handleSeverityFilter(sev)}
                      sx={{
                        borderRadius: 1.5, fontSize: '0.7rem', fontWeight: 600,
                        cursor: 'pointer',
                        bgcolor: cfg.bg, color: cfg.color,
                        border: `1px solid ${cfg.border}`,
                        transition: 'all 0.2s',
                        '&:hover': {
                          bgcolor: `${cfg.color}22`,
                          border: `1px solid ${cfg.color}60`,
                          transform: 'translateY(-1px)',
                        },
                      }}
                    />
                  );
                })}
              </Box>
              <Divider sx={{ mb: 1.5, borderColor: 'var(--border-subtle)' }} />
            </Box>
          </Fade>
        )}

        {/* ── Tool call indicators ── */}
        {toolLog.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.6, flexWrap: 'wrap', mb: 1 }}>
            {toolLog.slice(0, 4).map((tool, i) => (
              <Chip key={i} label={tool} size="small" variant="outlined"
                sx={{ borderRadius: 1.5, fontSize: '0.68rem', height: 22, borderColor: 'rgba(45,212,191,0.25)', color: 'var(--accent-secondary)' }} />
            ))}
            {toolLog.length > 4 && (
              <Chip label={`+${toolLog.length - 4}`} size="small"
                sx={{ borderRadius: 1.5, fontSize: '0.68rem', height: 22, bgcolor: 'rgba(255,255,255,0.05)', color: 'var(--text-muted)' }} />
            )}
          </Box>
        )}

        {/* ── Agent execution timeline ── */}
        {traceEvents.length > 0 && (
          <AgentTimeline
            traceEvents={traceEvents}
            collapsed={!timelineOpen}
            onToggle={() => setTimelineOpen((prev) => !prev)}
          />
        )}

        {/* ── Review output area ── */}
        <Box sx={{ position: 'relative', flex: 1, minHeight: 300, overflow: 'hidden' }}>
          <Box
            ref={reviewAreaRef}
            onScroll={handleScrollAreaScroll}
            className="custom-scrollbar"
            sx={{
              position: 'absolute', inset: 0,
              overflow: 'auto', p: 2.5, borderRadius: 2,
              bgcolor: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-subtle)',
            }}
          >
            {/* ── Empty state ── */}
            {!isStreaming && !cleanContent && (
              <Box sx={{
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                height: '100%', minHeight: 200,
              }}>
                <Box sx={{
                  width: 56, height: 56, borderRadius: 2, mb: 2.5,
                  background: 'rgba(212,160,83,0.04)', border: '1px solid rgba(212,160,83,0.08)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <BugReportIcon sx={{ fontSize: 26, color: 'var(--text-muted)' }} />
                </Box>
                <Typography sx={{ fontSize: '0.87rem', color: 'var(--text-muted)', mb: 0.5, fontWeight: 600, fontFamily: 'var(--font-display)' }}>
                  输入仓库路径，点击「开始审查」
                </Typography>
                <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)', opacity: 0.5 }}>
                  支持本地路径和 GitHub URL，审查结果实时流式输出
                </Typography>
              </Box>
            )}

            {/* ── Streaming / completed content ── */}
            {cleanContent && (
              <Box>
                <MarkdownContent text={cleanContent} />
                {isStreaming && (
                  <Box component="span" sx={{
                    display: 'inline-block', width: 8, height: 16,
                    bgcolor: 'var(--accent)', ml: 0.3, borderRadius: 1.5,
                    animation: 'cursorBlink 1s step-end infinite', verticalAlign: 'text-bottom',
                  }} />
                )}
              </Box>
            )}

            {/* ── Connecting/scanning state (before any text arrives) ── */}
            {((phase === 'connecting' || phase === 'scanning' || (streamLoading && !cleanContent))) && (
              <Fade in timeout={500}>
                <Box sx={{
                  display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                  height: '100%', minHeight: 200, gap: 1.5,
                }}>
                  <Box sx={{
                    width: 48, height: 48, borderRadius: '50%',
                    bgcolor: 'rgba(212,160,83,0.08)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    animation: 'phasePulse 2s ease-in-out infinite',
                    '@keyframes phasePulse': {
                      '0%, 100%': { boxShadow: '0 0 0 0 rgba(212,160,83,0.3)' },
                      '50%': { boxShadow: '0 0 0 12px rgba(212,160,83,0)' },
                    },
                  }}>
                    {phase === 'connecting' ? (
                      <HourglassEmptyIcon sx={{ fontSize: 22, color: 'var(--accent)' }} />
                    ) : phase === 'scanning' ? (
                      <FolderOpenIcon sx={{ fontSize: 22, color: 'var(--accent)' }} />
                    ) : (
                      <PsychologyIcon sx={{ fontSize: 22, color: 'var(--accent)' }} />
                    )}
                  </Box>
                  <Box className="thinking-dots">
                    <Box component="span" className="dot" />
                    <Box component="span" className="dot" />
                    <Box component="span" className="dot" />
                  </Box>
                  <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 500 }}>
                    {PHASE_CONFIG[phase]?.desc || '正在分析仓库...'}
                  </Typography>
                  <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)', opacity: 0.5 }}>
                    {phase === 'connecting' ? '正在建立安全连接并与 AI 服务握手' :
                     phase === 'scanning' ? '读取仓库文件列表、Git 状态与变更差异' :
                     'AI 正在理解代码结构，这可能需要几十秒'}
                  </Typography>
                </Box>
              </Fade>
            )}

            <div ref={endRef} />
          </Box>

          {/* ── New content notification (when user scrolled up) ── */}
          <Fade in={newContentWhileScrolledUp && isStreaming}>
            <Box
              onClick={() => { setIsUserScrolledUp(false); setNewContentWhileScrolledUp(false); scrollToBottom(true); }}
              sx={{
                position: 'absolute', bottom: 12, left: '50%', transform: 'translateX(-50%)',
                px: 2, py: 0.8, borderRadius: 2,
                bgcolor: 'var(--accent)', color: '#0c0b0e',
                fontSize: '0.75rem', fontWeight: 700,
                cursor: 'pointer', boxShadow: '0 4px 16px rgba(212,160,83,0.3)',
                display: 'flex', alignItems: 'center', gap: 0.5,
                transition: 'all 0.2s',
                '&:hover': { transform: 'translateX(-50%) translateY(-1px)' },
                zIndex: 10,
              }}
            >
              <KeyboardArrowDownIcon sx={{ fontSize: 16 }} />
              新内容
            </Box>
          </Fade>

          {/* ── Scroll to bottom FAB ── */}
          <Zoom in={showScrollBtn && !newContentWhileScrolledUp}>
            <Box
              onClick={() => { setIsUserScrolledUp(false); setNewContentWhileScrolledUp(false); scrollToBottom(true); }}
              sx={{
                position: 'absolute', bottom: 12, right: 12,
                width: 34, height: 34, borderRadius: '50%',
                bgcolor: 'var(--bg-elevated)', color: 'var(--text-primary)',
                border: '1px solid var(--border-subtle)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer',
                boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
                transition: 'all 0.2s',
                '&:hover': { bgcolor: 'var(--accent)', color: '#0c0b0e', borderColor: 'var(--accent)' },
                zIndex: 10,
              }}
            >
              <KeyboardArrowDownIcon sx={{ fontSize: 20 }} />
            </Box>
          </Zoom>
        </Box>

        {/* ── Action bar (after review completes) ── */}
        {cleanContent && !isStreaming && (
          <Box sx={{ display: 'flex', gap: 1, mt: 1.5, justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              {totalIssues > 0 ? `共发现 ${totalIssues} 个问题` : '审查完成'}
              {toolCallCount > 0 && ` · 调用了 ${toolCallCount} 个工具`}
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Tooltip title="复制审查结果" arrow>
                <IconButton size="small" onClick={handleCopy}
                  sx={{ color: copied ? 'var(--success)' : 'var(--text-muted)', '&:hover': { color: 'var(--accent)' } }}>
                  {copied ? <CheckIcon sx={{ fontSize: 16 }} /> : <ContentCopyIcon sx={{ fontSize: 16 }} />}
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
