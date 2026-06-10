import React, { useState } from 'react';
import { Dialog, Box, Typography } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useLoginUser } from '../stores/useLoginUser.jsx';

/**
 * Fallback login modal (shown when API returns 401).
 * Uses the same design language as the split-screen login.
 */
export default function LoginModal({ open, onClose }) {
  const [userAccount, setUserAccount] = useState('');
  const [userPassword, setUserPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login } = useLoginUser();

  const handleClose = () => {
    setUserAccount('');
    setUserPassword('');
    setError('');
    setSubmitting(false);
    onClose();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(userAccount, userPassword);
      handleClose();
    } catch (err) {
      setError(err.message || '登录失败');
      setSubmitting(false);
    }
  };

  const inputStyle = {
    width: '100%', padding: '11px 14px', borderRadius: 'var(--radius-input)',
    border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.04)',
    color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.9rem',
    outline: 'none', transition: 'border-color 0.2s',
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      maxWidth="xs"
      fullWidth
      PaperProps={{
        sx: {
          background: 'var(--bg-surface)',
          backdropFilter: 'blur(20px)',
          border: '1px solid var(--border-subtle)',
          borderRadius: 'var(--radius-card)',
          p: 0,
        },
      }}
    >
      <Box sx={{ p: '28px 28px 24px' }}>
        {/* Close button */}
        <Box
          onClick={handleClose}
          sx={{
            position: 'absolute', top: 16, right: 16,
            width: 28, height: 28, borderRadius: '50%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer', color: 'var(--text-muted)',
            transition: 'all 0.2s',
            '&:hover': { color: 'var(--text-primary)', bgcolor: 'rgba(255,255,255,0.06)' },
          }}
        >
          <CloseIcon sx={{ fontSize: 16 }} />
        </Box>

        <Typography
          sx={{
            fontFamily: 'var(--font-display)',
            fontSize: '1.3rem', fontWeight: 700,
            color: 'var(--text-primary)', mb: 0.5,
          }}
        >
          需要登录
        </Typography>
        <Typography sx={{ fontSize: '0.82rem', color: 'var(--text-muted)', mb: 3 }}>
          此操作需要登录账号后使用
        </Typography>

        <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {error && (
            <Box sx={{ p: '10px 14px', borderRadius: 'var(--radius-btn)', bgcolor: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.2)' }}>
              <Typography sx={{ fontSize: '0.82rem', color: '#fca5a5' }}>{error}</Typography>
            </Box>
          )}

          <Box>
            <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', mb: 0.8 }}>账号</Typography>
            <input
              type="text"
              value={userAccount}
              onChange={(e) => setUserAccount(e.target.value)}
              required
              autoFocus
              placeholder="输入你的账号"
              style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
              onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
            />
          </Box>

          <Box>
            <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', mb: 0.8 }}>密码</Typography>
            <input
              type="password"
              value={userPassword}
              onChange={(e) => setUserPassword(e.target.value)}
              required
              placeholder="输入密码"
              style={inputStyle}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
              onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
            />
          </Box>

          <button
            type="submit"
            disabled={submitting}
            className="gradient-btn"
            style={{ width: '100%', padding: '11px', border: 'none', cursor: submitting ? 'not-allowed' : 'pointer', fontSize: '0.9rem' }}
          >
            {submitting ? '处理中...' : '登 录'}
          </button>

          <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)', textAlign: 'center' }}>
            测试账号: user / 12345678
          </Typography>
        </Box>
      </Box>
    </Dialog>
  );
}
