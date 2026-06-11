import React, { useState } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, CircularProgress,
  Alert, LinearProgress,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import BugReportIcon from '@mui/icons-material/BugReport';
import { reviewCode } from '../api/client.js';
import FavoriteReposBar from './FavoriteReposBar.jsx';

export default function ReviewPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleReview = async () => {
    if (!repoPath.trim()) { setError('请输入代码仓库路径'); return; }
    setLoading(true); setError(null); setResult(null);
    try {
      const data = await reviewCode(repoPath.trim());
      setResult(data);
    } catch (err) {
      setError(err.message || '代码审查失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="forge-card" style={{ height: '100%' }}>
      <CardContent sx={{ p: '22px 24px !important' }}>
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
            disabled={loading}
            size="small"
            sx={{ flex: 1 }}
            helperText={repoPath.trim().match(/github\.com/) ? '🌐 GitHub 仓库将通过 MCP 远程读取' : ''}
            FormHelperTextProps={{ sx: { fontSize: '0.68rem', color: 'var(--accent-secondary)', ml: 0.5 } }}
          />
          <Button
            variant="contained"
            onClick={handleReview}
            disabled={loading}
            className="gradient-btn"
            sx={{ minWidth: 130 }}
            startIcon={loading ? <CircularProgress size={16} sx={{ color: '#0c0b0e' }} /> : <SearchIcon />}
          >
            {loading ? '审查中...' : '开始审查'}
          </Button>
        </Box>

        {/* Loading bar */}
        {loading && (
          <LinearProgress
            sx={{
              mb: 2, borderRadius: 2, height: 3,
              bgcolor: 'rgba(212,160,83,0.1)',
              '& .MuiLinearProgress-bar': { bgcolor: 'var(--accent)' },
            }}
          />
        )}

        {/* Error */}
        {error && (
          <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }} onClose={() => setError(null)}>{error}</Alert>
        )}

        {/* Result */}
        {result && (
          <Box>
            <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
              <Box sx={{
                px: 1.5, py: 0.4, borderRadius: 1,
                bgcolor: 'rgba(212,160,83,0.1)', border: '1px solid rgba(212,160,83,0.15)',
              }}>
                <Typography sx={{ fontSize: '0.72rem', fontWeight: 600, color: 'var(--accent)' }}>
                  {result.sessionId ? `Session: ${result.sessionId}` : 'Session: -'}
                </Typography>
              </Box>
              <Box sx={{
                px: 1.5, py: 0.4, borderRadius: 1,
                bgcolor: 'rgba(45,212,191,0.08)', border: '1px solid rgba(45,212,191,0.12)',
              }}>
                <Typography sx={{ fontSize: '0.72rem', fontWeight: 600, color: 'var(--accent-secondary)' }}>
                  {result.repoPath || '-'}
                </Typography>
              </Box>
            </Box>
            <Box
              component="pre"
              className="custom-scrollbar"
              sx={{
                p: 2.5,
                borderRadius: 2,
                bgcolor: 'rgba(0,0,0,0.25)',
                border: '1px solid var(--border-subtle)',
                fontSize: '0.82rem',
                lineHeight: 1.8,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                maxHeight: 600,
                overflow: 'auto',
                color: 'var(--text-primary)',
                fontFamily: 'var(--font-code)',
              }}
            >
              {result.review || '审查结果为空'}
            </Box>
          </Box>
        )}

        {/* Empty state */}
        {!loading && !error && !result && (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <Box sx={{
              width: 52, height: 52, borderRadius: 2, mx: 'auto', mb: 2,
              background: 'rgba(212,160,83,0.06)',
              border: '1px solid rgba(212,160,83,0.1)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <BugReportIcon sx={{ fontSize: 24, color: 'var(--text-muted)' }} />
            </Box>
            <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              输入本地仓库路径或 GitHub URL，点击「开始审查」
            </Typography>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
