import React, { useState, useEffect } from 'react';
import { CardContent, Typography, Box, CircularProgress } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
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
    <div className={isHealthy ? 'gradient-border-card' : 'glass-card'}>
      <CardContent sx={{ p: '16px 24px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {loading ? (
              <CircularProgress size={20} sx={{ color: '#6366f1' }} />
            ) : isHealthy ? (
              <Box sx={{ position: 'relative' }}>
                <CheckCircleIcon sx={{ fontSize: 22, color: '#22c55e', zIndex: 1, position: 'relative' }} />
                <Box
                  sx={{
                    position: 'absolute', top: -4, left: -4, width: 30, height: 30,
                    borderRadius: '50%', bgcolor: 'rgba(34,197,94,0.15)',
                    animation: 'pulseGlow 2s ease-in-out infinite',
                  }}
                />
              </Box>
            ) : (
              <Box sx={{ position: 'relative' }}>
                <ErrorIcon sx={{ fontSize: 22, color: '#ef4444', zIndex: 1, position: 'relative' }} />
                <Box
                  sx={{
                    position: 'absolute', top: -4, left: -4, width: 30, height: 30,
                    borderRadius: '50%', bgcolor: 'rgba(239,68,68,0.15)',
                  }}
                />
              </Box>
            )}

            <Box>
              <Typography className="section-title" sx={{ fontSize: '0.95rem' }}>
                服务健康状态
              </Typography>
              <Typography variant="caption" sx={{ color: darkMode ? '#94a3b8' : '#64748b' }}>
                每 30 秒自动检测
              </Typography>
            </Box>
          </Box>

          {/* Status indicator */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {!loading && (
              <>
                <FiberManualRecordIcon
                  sx={{
                    fontSize: 10,
                    color: isHealthy ? '#22c55e' : '#ef4444',
                    filter: `drop-shadow(0 0 4px ${isHealthy ? '#22c55e' : '#ef4444'})`,
                  }}
                />
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    color: isHealthy
                      ? (darkMode ? '#86efac' : '#16a34a')
                      : (darkMode ? '#fca5a5' : '#dc2626'),
                  }}
                >
                  {isHealthy ? '运行正常' : error || '连接异常'}
                </Typography>
              </>
            )}
            {loading && (
              <Typography variant="body2" sx={{ color: darkMode ? '#94a3b8' : '#94a3b8' }}>
                检测中...
              </Typography>
            )}
          </Box>
        </Box>
      </CardContent>
    </div>
  );
}
