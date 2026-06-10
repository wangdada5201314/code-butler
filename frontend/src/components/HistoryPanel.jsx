import React, { useState, useEffect, useCallback } from 'react';
import {
  Typography, Box, Chip, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Pagination,
  CircularProgress, IconButton, Tooltip,
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import BugReportIcon from '@mui/icons-material/BugReport';
import ChatIcon from '@mui/icons-material/Chat';
import DescriptionIcon from '@mui/icons-material/Description';
import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { getHistory } from '../api/client.js';
import { useLoginUser } from '../stores/useLoginUser.jsx';

/** 操作类型配置 */
const OP_CONFIG = {
  REVIEW: { label: '代码审查', icon: BugReportIcon, color: 'var(--accent)', bg: 'rgba(212,160,83,0.12)' },
  CHAT:   { label: '智能问答', icon: ChatIcon,       color: 'var(--accent-secondary)', bg: 'rgba(45,212,191,0.12)' },
  DOC:    { label: '文档生成', icon: DescriptionIcon, color: '#a78bfa', bg: 'rgba(167,139,250,0.12)' },
};

/** 格式化耗时 */
function formatDuration(ms) {
  if (!ms || ms <= 0) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

/** 格式化时间 */
function formatTime(timeStr) {
  if (!timeStr) return '-';
  const d = new Date(timeStr);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${month}-${day} ${hour}:${min}`;
}

export default function HistoryPanel({ darkMode, open, onClose }) {
  const { loginUser } = useLoginUser();
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [expandedId, setExpandedId] = useState(null);
  const pageSize = 15;

  const fetchHistory = useCallback(async (p) => {
    setLoading(true);
    try {
      const data = await getHistory(p, pageSize);
      setRecords(data.records || []);
      setTotal(data.totalRow || 0);
    } catch (err) {
      console.error('获取历史记录失败:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open && loginUser) {
      fetchHistory(page);
    }
  }, [open, page, loginUser, fetchHistory]);

  useEffect(() => {
    if (open) {
      setPage(1);
      setExpandedId(null);
    }
  }, [open]);

  if (!open) return null;

  const totalPages = Math.ceil(total / pageSize);

  return (
    <Box
      sx={{
        position: 'fixed', top: 0, right: 0, bottom: 0,
        width: { xs: '100%', sm: 480 },
        zIndex: 1300,
        background: 'var(--bg-deep)',
        backdropFilter: 'blur(20px)',
        borderLeft: '1px solid var(--border-subtle)',
        boxShadow: '-8px 0 32px rgba(0,0,0,0.3)',
        display: 'flex', flexDirection: 'column',
        animation: 'slideIn 0.3s cubic-bezier(0.22, 1, 0.36, 1)',
      }}
    >
      {/* Header */}
      <Box sx={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        p: '16px 20px', borderBottom: '1px solid var(--border-subtle)',
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: 'rgba(212,160,83,0.1)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <HistoryIcon sx={{ color: 'var(--accent)', fontSize: 18 }} />
          </Box>
          <Typography sx={{ fontFamily: 'var(--font-display)', fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            操作历史
          </Typography>
          <Chip
            label={`${total} 条`}
            size="small"
            sx={{
              bgcolor: 'rgba(212,160,83,0.08)', color: 'var(--accent)',
              fontSize: '0.7rem', fontWeight: 600,
              border: '1px solid rgba(212,160,83,0.15)',
            }}
          />
        </Box>
        <Tooltip title="关闭">
          <IconButton
            size="small"
            onClick={onClose}
            sx={{
              color: 'var(--text-muted)',
              '&:hover': { color: 'var(--text-primary)', bgcolor: 'rgba(255,255,255,0.04)' },
            }}
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, overflow: 'auto', p: '12px 16px' }} className="custom-scrollbar">
        {!loginUser ? (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <HistoryIcon sx={{ fontSize: 44, color: 'var(--text-muted)', mb: 2, opacity: 0.5 }} />
            <Typography variant="body2" sx={{ color: 'var(--text-muted)' }}>
              请先登录查看操作历史
            </Typography>
          </Box>
        ) : loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress size={24} sx={{ color: 'var(--accent)' }} />
          </Box>
        ) : records.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 8 }}>
            <HistoryIcon sx={{ fontSize: 44, color: 'var(--text-muted)', mb: 2, opacity: 0.5 }} />
            <Typography variant="body2" sx={{ color: 'var(--text-muted)', mb: 0.5 }}>
              暂无操作记录
            </Typography>
            <Typography variant="caption" sx={{ color: 'var(--text-muted)', opacity: 0.6 }}>
              使用代码审查、智能问答或文档生成后
              <br />
              记录会自动保存在这里
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ color: 'var(--text-muted)', fontSize: '0.7rem', fontWeight: 600, border: 'none', pb: 1, letterSpacing: '0.03em' }}>类型</TableCell>
                  <TableCell sx={{ color: 'var(--text-muted)', fontSize: '0.7rem', fontWeight: 600, border: 'none', pb: 1, letterSpacing: '0.03em' }}>仓库 / 输入</TableCell>
                  <TableCell sx={{ color: 'var(--text-muted)', fontSize: '0.7rem', fontWeight: 600, border: 'none', pb: 1, textAlign: 'right', letterSpacing: '0.03em' }}>时间</TableCell>
                  <TableCell sx={{ width: 28, border: 'none', p: 0 }}></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {records.map((r) => {
                  const cfg = OP_CONFIG[r.opType] || OP_CONFIG.CHAT;
                  const IconComp = cfg.icon;
                  const isExpanded = expandedId === r.id;

                  return (
                    <React.Fragment key={r.id}>
                      <TableRow
                        hover
                        onClick={() => setExpandedId(isExpanded ? null : r.id)}
                        sx={{
                          cursor: 'pointer',
                          transition: 'background 0.2s',
                          '&:hover': { bgcolor: 'rgba(255,255,255,0.02)' },
                          '& td': { border: 'none', py: '9px' },
                        }}
                      >
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
                            <Box sx={{
                              width: 26, height: 26, borderRadius: 1,
                              bgcolor: cfg.bg,
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                            }}>
                              <IconComp sx={{ fontSize: 13, color: cfg.color }} />
                            </Box>
                            <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: cfg.color, fontFamily: 'var(--font-body)' }}>
                              {cfg.label}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Typography sx={{
                            fontSize: '0.73rem', color: 'var(--text-secondary)',
                            maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                          }}>
                            {r.input || r.repoPath || '-'}
                          </Typography>
                          <Typography sx={{
                            fontSize: '0.65rem', color: 'var(--text-muted)',
                          }}>
                            {formatDuration(r.durationMs)}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ textAlign: 'right' }}>
                          <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                            {formatTime(r.createTime)}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ p: 0 }}>
                          {isExpanded
                            ? <ExpandLessIcon sx={{ fontSize: 15, color: 'var(--text-muted)' }} />
                            : <ExpandMoreIcon sx={{ fontSize: 15, color: 'var(--text-muted)' }} />}
                        </TableCell>
                      </TableRow>

                      {/* Expanded detail */}
                      {isExpanded && (
                        <TableRow>
                          <TableCell colSpan={4} sx={{ border: 'none', pt: 0, pb: 2, px: 1 }}>
                            <Box sx={{
                              p: 1.5, borderRadius: 1.5,
                              bgcolor: 'rgba(0,0,0,0.15)',
                              border: '1px solid var(--border-subtle)',
                            }}>
                              {r.repoPath && (
                                <Box sx={{ mb: 1 }}>
                                  <Typography sx={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.03em', textTransform: 'uppercase' }}>仓库路径</Typography>
                                  <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontFamily: 'var(--font-code)' }}>{r.repoPath}</Typography>
                                </Box>
                              )}
                              {r.outputSummary && (
                                <Box>
                                  <Typography sx={{ fontSize: '0.65rem', color: 'var(--text-muted)', fontWeight: 600, mb: 0.5, letterSpacing: '0.03em', textTransform: 'uppercase' }}>AI 输出摘要</Typography>
                                  <Typography sx={{
                                    fontSize: '0.75rem', color: 'var(--text-secondary)',
                                    lineHeight: 1.7, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                                    maxHeight: 200, overflow: 'auto',
                                  }}>
                                    {r.outputSummary}
                                  </Typography>
                                </Box>
                              )}
                              {!r.outputSummary && !r.repoPath && (
                                <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                  无详细信息
                                </Typography>
                              )}
                            </Box>
                          </TableCell>
                        </TableRow>
                      )}
                    </React.Fragment>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>

      {/* Pagination */}
      {totalPages > 1 && (
        <Box sx={{
          display: 'flex', justifyContent: 'center', py: 1.5,
          borderTop: '1px solid var(--border-subtle)',
        }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_, p) => setPage(p)}
            size="small"
            sx={{
              '& .MuiPaginationItem-root': {
                color: 'var(--text-muted)',
                fontSize: '0.75rem',
              },
              '& .Mui-selected': {
                bgcolor: 'rgba(212,160,83,0.15) !important',
                color: 'var(--accent) !important',
              },
            }}
          />
        </Box>
      )}
    </Box>
  );
}
