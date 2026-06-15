import React, { useEffect, useRef, useMemo } from 'react';
import {
  Box, Typography, Chip, Collapse, IconButton, Paper,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

/* ─── Event type configuration ─── */
const EVENT_CONFIG = {
  REASONING_START:  { color: '#3b82f6', icon: '\u{1F9E0}', label: 'Reasoning' },
  REASONING_END:    { color: '#3b82f6', icon: '\u2713',     label: 'Reasoning' },
  TOOL_CALL_START:  { color: '#22c55e', icon: '\u{1F527}', label: 'Tool Call' },
  TOOL_CALL_END:    { color: '#22c55e', icon: '\u2713',     label: 'Tool Call' },
  SUBAGENT_START:   { color: '#a855f7', icon: '\u{1F916}', label: 'Sub-Agent' },
  SUBAGENT_END:     { color: '#a855f7', icon: '\u2713',     label: 'Sub-Agent' },
  PLAN_UPDATE:      { color: '#f97316', icon: '\u{1F4CB}', label: 'Plan' },
};

const DEFAULT_CONFIG = { color: '#6b7280', icon: '\u25CF', label: 'Event' };

/* ─── Relative time formatter ─── */
function formatRelativeTime(ts, startTs) {
  if (!ts || !startTs) return '';
  const diffMs = ts - startTs;
  if (diffMs < 0) return '+0s';
  if (diffMs < 1000) return `+${diffMs}ms`;
  return `+${(diffMs / 1000).toFixed(1)}s`;
}

/* ─── Duration formatter ─── */
function formatDuration(ms) {
  if (ms == null) return null;
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

/* ─── Truncate args summary ─── */
function truncateArgs(args, maxLen = 60) {
  if (!args) return null;
  const str = typeof args === 'string' ? args : JSON.stringify(args);
  if (str.length <= maxLen) return str;
  return str.slice(0, maxLen) + '...';
}

/* ========================================================
   AgentTimeline Component
   ======================================================== */
export default function AgentTimeline({ traceEvents = [], collapsed = false, onToggle }) {
  const scrollRef = useRef(null);
  const prevCountRef = useRef(traceEvents.length);

  const startTimestamp = useMemo(() => {
    if (traceEvents.length === 0) return null;
    return traceEvents[0].timestamp || Date.now();
  }, [traceEvents]);

  // Auto-scroll to bottom when new events arrive
  useEffect(() => {
    if (!collapsed && traceEvents.length > prevCountRef.current && scrollRef.current) {
      requestAnimationFrame(() => {
        scrollRef.current?.scrollTo({
          top: scrollRef.current.scrollHeight,
          behavior: 'smooth',
        });
      });
    }
    prevCountRef.current = traceEvents.length;
  }, [traceEvents.length, collapsed]);

  if (traceEvents.length === 0) return null;

  return (
    <Paper
      variant="outlined"
      sx={{
        mb: 1.5,
        borderRadius: 2,
        border: '1px solid var(--border-subtle)',
        bgcolor: 'rgba(0,0,0,0.15)',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <Box
        onClick={onToggle}
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 1.5,
          py: 0.8,
          cursor: 'pointer',
          userSelect: 'none',
          bgcolor: 'rgba(0,0,0,0.1)',
          borderBottom: collapsed ? 'none' : '1px solid var(--border-subtle)',
          transition: 'background 0.2s',
          '&:hover': { bgcolor: 'rgba(0,0,0,0.18)' },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography sx={{
            fontSize: '0.78rem',
            fontWeight: 700,
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-display)',
          }}>
            Agent 执行追踪
          </Typography>
          <Chip
            label={traceEvents.length}
            size="small"
            sx={{
              height: 20,
              minWidth: 28,
              fontSize: '0.68rem',
              fontWeight: 700,
              bgcolor: 'rgba(45,212,191,0.12)',
              color: 'var(--accent-secondary)',
              border: '1px solid rgba(45,212,191,0.2)',
              '& .MuiChip-label': { px: 0.8 },
            }}
          />
        </Box>
        <IconButton
          size="small"
          sx={{
            color: 'var(--text-muted)',
            p: 0.3,
            transition: 'transform 0.25s ease',
            transform: collapsed ? 'rotate(0deg)' : 'rotate(180deg)',
          }}
        >
          <ExpandMoreIcon sx={{ fontSize: 18 }} />
        </IconButton>
      </Box>

      {/* Timeline body */}
      <Collapse in={!collapsed} timeout={250} unmountOnExit>
        <Box
          ref={scrollRef}
          className="custom-scrollbar"
          sx={{
            maxHeight: 400,
            overflowY: 'auto',
            px: 1.5,
            py: 1,
          }}
        >
          {traceEvents.map((event, idx) => {
            const config = EVENT_CONFIG[event.type] || DEFAULT_CONFIG;
            const isLast = idx === traceEvents.length - 1;
            const isNewlyAdded = idx >= prevCountRef.current - 1;

            return (
              <Box
                key={`${event.type}-${event.timestamp}-${idx}`}
                sx={{
                  display: 'flex',
                  gap: 1.2,
                  position: 'relative',
                  animation: isNewlyAdded ? 'timelineSlideIn 0.3s ease-out' : 'none',
                  '@keyframes timelineSlideIn': {
                    from: { opacity: 0, transform: 'translateX(-8px)' },
                    to: { opacity: 1, transform: 'translateX(0)' },
                  },
                }}
              >
                {/* Vertical line + node */}
                <Box sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  width: 22,
                  flexShrink: 0,
                  position: 'relative',
                }}>
                  {/* Node circle */}
                  <Box sx={{
                    width: 22,
                    height: 22,
                    borderRadius: '50%',
                    bgcolor: `${config.color}22`,
                    border: `2px solid ${config.color}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.65rem',
                    lineHeight: 1,
                    flexShrink: 0,
                    zIndex: 1,
                    mt: '3px',
                  }}>
                    {config.icon}
                  </Box>
                  {/* Connecting line */}
                  {!isLast && (
                    <Box sx={{
                      width: 2,
                      flex: 1,
                      bgcolor: 'var(--border-subtle)',
                      minHeight: 14,
                    }} />
                  )}
                </Box>

                {/* Content */}
                <Box sx={{
                  flex: 1,
                  minHeight: 32,
                  pb: isLast ? 0 : 0.5,
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'center',
                }}>
                  {/* Top row: name + relative time */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, flexWrap: 'wrap' }}>
                    <Typography sx={{
                      fontSize: '0.76rem',
                      fontWeight: 600,
                      color: 'var(--text-primary)',
                    }}>
                      {event.name || config.label}
                    </Typography>

                    <Typography sx={{
                      fontSize: '0.65rem',
                      color: 'var(--text-muted)',
                      fontFamily: 'var(--font-code)',
                    }}>
                      {formatRelativeTime(event.timestamp, startTimestamp)}
                    </Typography>

                    {/* Duration chip */}
                    {event.data?.elapsedMs != null && (
                      <Chip
                        label={formatDuration(event.data.elapsedMs)}
                        size="small"
                        sx={{
                          height: 18,
                          fontSize: '0.62rem',
                          fontWeight: 600,
                          bgcolor: 'rgba(255,255,255,0.06)',
                          color: 'var(--text-secondary)',
                          border: '1px solid var(--border-subtle)',
                          '& .MuiChip-label': { px: 0.6 },
                        }}
                      />
                    )}

                    {/* Status chip */}
                    {event.data?.status && (
                      <Chip
                        label={event.data.status}
                        size="small"
                        sx={{
                          height: 18,
                          fontSize: '0.62rem',
                          fontWeight: 700,
                          bgcolor: event.data.status === 'success'
                            ? 'rgba(34,197,94,0.12)'
                            : event.data.status === 'error'
                              ? 'rgba(239,68,68,0.12)'
                              : 'rgba(255,255,255,0.06)',
                          color: event.data.status === 'success'
                            ? '#22c55e'
                            : event.data.status === 'error'
                              ? '#ef4444'
                              : 'var(--text-secondary)',
                          border: `1px solid ${
                            event.data.status === 'success'
                              ? 'rgba(34,197,94,0.3)'
                              : event.data.status === 'error'
                                ? 'rgba(239,68,68,0.3)'
                                : 'var(--border-subtle)'
                          }`,
                          '& .MuiChip-label': { px: 0.6 },
                        }}
                      />
                    )}
                  </Box>

                  {/* Args summary */}
                  {event.data?.args && (
                    <Typography sx={{
                      fontSize: '0.68rem',
                      color: 'var(--text-muted)',
                      fontFamily: 'var(--font-code)',
                      mt: 0.3,
                      wordBreak: 'break-all',
                      lineHeight: 1.4,
                    }}>
                      {truncateArgs(event.data.args)}
                    </Typography>
                  )}
                </Box>
              </Box>
            );
          })}
        </Box>
      </Collapse>
    </Paper>
  );
}
