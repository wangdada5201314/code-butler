import React, { useState, useEffect } from 'react';
import { CardContent, Typography, Box, CircularProgress } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import { checkHealth } from '../api/client.js';

export default function HealthCard({ darkMode }) {
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function fetchHealth() {
      try {
        setLoading(true);
        setError(null);
        const data = await checkHealth();
        if (!cancelled) setHealth(data);
      } catch (err) {
        if (!cancelled) setError(err.message || 'Health check failed');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    fetchHealth();
    const interval = setInterval(fetchHealth, 30000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  const isHealthy = !error && health !== null;

  return (
    <div className={isHealthy ? 'gradient-border-card' : 'forge-card'}>
      <CardContent sx={{ p: '14px 24px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {loading ? (
              <CircularProgress size={18} sx={{ color: 'var(--accent)' }} />
            ) : isHealthy ? (
              <Box sx={{ position: 'relative' }}>
                <CheckCircleIcon sx={{ fontSize: 20, color: 'var(--success)', zIndex: 1, position: 'relative' }} />
                <Box
                  sx={{
                    position: 'absolute', top: -3, left: -3, width: 26, height: 26,
                    borderRadius: '50%', bgcolor: 'rgba(74,222,128,0.12)',
                    animation: 'forgeGlow 2.5s ease-in-out infinite',
                  }}
                />
              </Box>
            ) : (
              <Box sx={{ position: 'relative' }}>
                <ErrorIcon sx={{ fontSize: 20, color: 'var(--danger)', zIndex: 1, position: 'relative' }} />
                <Box
                  sx={{
                    position: 'absolute', top: -3, left: -3, width: 26, height: 26,
                    borderRadius: '50%', bgcolor: 'rgba(248,113,113,0.12)',
                  }}
                />
              </Box>
            )}

            <Box>
              <Typography
                sx={{
                  fontFamily: 'var(--font-display)',
                  fontSize: '0.9rem', fontWeight: 700,
                  color: 'var(--text-primary)',
                }}
              >
                服务健康状态
              </Typography>
              <Typography variant="caption" sx={{ color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                每 30 秒自动检测
              </Typography>
            </Box>
          </Box>

          {/* Status indicator */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {!loading && (
              <>
                <Box sx={{
                  width: 8, height: 8, borderRadius: '50%',
                  bgcolor: isHealthy ? 'var(--success)' : 'var(--danger)',
                  boxShadow: `0 0 8px ${isHealthy ? 'rgba(74,222,128,0.5)' : 'rgba(248,113,113,0.5)'}`,
                }} />
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 600, fontSize: '0.82rem',
                    color: isHealthy ? 'var(--success)' : 'var(--danger)',
                  }}
                >
                  {isHealthy ? '运行正常' : error || '连接异常'}
                </Typography>
              </>
            )}
            {loading && (
              <Typography variant="body2" sx={{ color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                检测中...
              </Typography>
            )}
          </Box>
        </Box>
      </CardContent>
    </div>
  );
}
