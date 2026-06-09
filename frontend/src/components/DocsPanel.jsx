import React, { useState } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, CircularProgress,
  Alert, FormControl, InputLabel, Select, MenuItem, Paper, Chip,
} from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import { generateDocs } from '../api/client.js';

export default function DocsPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [docType, setDocType] = useState('README');
  const [docContent, setDocContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);

  const handleGenerate = async () => {
    if (!repoPath.trim()) { setError('请输入代码仓库路径'); return; }
    setLoading(true); setError(null); setDocContent('');
    try {
      const data = await generateDocs(repoPath.trim(), docType);
      const content = typeof data === 'string' ? data : data?.content || data?.doc || JSON.stringify(data, null, 2);
      setDocContent(content);
    } catch (err) {
      setError(err.message || '文档生成失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (docContent) {
      navigator.clipboard.writeText(docContent).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }).catch(() => {});
    }
  };

  return (
    <div className="glass-card">
      <CardContent sx={{ p: '20px 24px !important' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2.5 }}>
          <DescriptionIcon sx={{ color: '#6366f1', fontSize: 22 }} />
          <Typography className="section-title">文档生成</Typography>
          <Box sx={{ flex: 1 }} />
          {docContent && (
            <Button
              size="small"
              onClick={handleCopy}
              startIcon={copied ? <CheckIcon sx={{ color: '#22c55e' }} fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
              sx={{
                textTransform: 'none',
                fontWeight: 600, fontSize: '0.8rem',
                color: copied ? '#22c55e' : '#6366f1',
                borderRadius: 1.5,
                '&:hover': { bgcolor: 'rgba(99,102,241,0.08)' },
              }}
            >
              {copied ? '已复制' : '复制'}
            </Button>
          )}
        </Box>

        {/* Input row */}
        <Box sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}>
          <TextField
            label="仓库路径"
            placeholder="E:/my-project"
            value={repoPath}
            onChange={(e) => setRepoPath(e.target.value)}
            disabled={loading}
            size="small"
            sx={{ flex: { xs: '1 1 100%', sm: 2 } }}
          />
          <FormControl size="small" sx={{ minWidth: 150 }} disabled={loading}>
            <InputLabel id="doc-type-label">文档类型</InputLabel>
            <Select labelId="doc-type-label" value={docType} label="文档类型" onChange={(e) => setDocType(e.target.value)}>
              <MenuItem value="README">README</MenuItem>
              <MenuItem value="CONTRIBUTING">CONTRIBUTING</MenuItem>
              <MenuItem value="API">API</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="contained"
            onClick={handleGenerate}
            disabled={loading}
            className="gradient-btn"
            sx={{ minWidth: 120 }}
            startIcon={loading ? <CircularProgress size={16} sx={{ color: '#fff' }} /> : <DescriptionIcon />}
          >
            {loading ? '生成中...' : '生成文档'}
          </Button>
        </Box>

        {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }} onClose={() => setError(null)}>{error}</Alert>}

        {/* Document output */}
        {docContent && (
          <Paper
            variant="outlined"
            sx={{
              borderRadius: 2, overflow: 'hidden',
              border: '1px solid rgba(99,102,241,0.15)',
            }}
          >
            {/* Top bar mimicking code editor */}
            <Box
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.8,
                px: 2, py: 1.2,
                bgcolor: darkMode ? 'rgba(15,15,30,0.9)' : 'rgba(30,27,75,0.95)',
              }}
            >
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ef4444' }} />
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#f59e0b' }} />
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#22c55e' }} />
              <Typography variant="caption" sx={{ ml: 1, color: 'rgba(255,255,255,0.6)', fontFamily: '"JetBrains Mono", monospace' }}>
                {docType}.md
              </Typography>
              <Box sx={{ flex: 1 }} />
              <Chip label={docType} size="small" sx={{ bgcolor: 'rgba(99,102,241,0.3)', color: '#a5b4fc', fontWeight: 600, fontSize: '0.65rem', height: 20 }} />
            </Box>

            {/* Content area */}
            <Box
              sx={{
                p: 2.5,
                bgcolor: darkMode ? 'rgba(0,0,0,0.2)' : 'rgba(15,23,42,0.04)',
                fontFamily: '"JetBrains Mono", "Fira Code", monospace',
                fontSize: '0.82rem',
                lineHeight: 1.7,
                whiteSpace: 'pre-wrap',
                overflow: 'auto',
                maxHeight: 360,
              }}
              className="custom-scrollbar"
            >
              <Typography
                component="pre"
                sx={{
                  m: 0,
                  fontFamily: 'inherit',
                  fontSize: 'inherit',
                  lineHeight: 'inherit',
                  color: darkMode ? '#e2e8f0' : '#1e293b',
                }}
              >
                {docContent}
              </Typography>
            </Box>
          </Paper>
        )}

        {/* Empty state */}
        {!loading && !error && !docContent && (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <DescriptionIcon sx={{ fontSize: 40, color: darkMode ? '#334155' : '#cbd5e1', mb: 1 }} />
            <Typography variant="body2" sx={{ color: darkMode ? '#64748b' : '#94a3b8' }}>
              选择文档类型并点击「生成文档」
            </Typography>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
