import React, { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, TextField, Button, Box, Typography,
  Alert, Tabs, Tab, IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useLoginUser } from '../stores/useLoginUser.jsx';

/**
 * Login/Register modal dialog.
 * @param {{ open: boolean, onClose: () => void, darkMode: boolean }} props
 */
export default function LoginModal({ open, onClose, darkMode }) {
  const [tab, setTab] = useState(0); // 0=login, 1=register
  const [userAccount, setUserAccount] = useState('');
  const [userPassword, setUserPassword] = useState('');
  const [checkPassword, setCheckPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { login, register, fetchLoginUser } = useLoginUser();

  const resetForm = () => {
    setUserAccount('');
    setUserPassword('');
    setCheckPassword('');
    setError('');
    setSubmitting(false);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      if (tab === 0) {
        // Login
        await login(userAccount, userPassword);
      } else {
        // Register
        if (userPassword !== checkPassword) {
          setError('两次输入的密码不一致');
          setSubmitting(false);
          return;
        }
        await register(userAccount, userPassword, checkPassword);
        // After registration, auto login
        await login(userAccount, userPassword);
      }
      handleClose();
    } catch (err) {
      setError(err.message || '操作失败');
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 0 }}>
        <Tabs value={tab} onChange={(_, v) => { setTab(v); setError(''); }}>
          <Tab label="登录" />
          <Tab label="注册" />
        </Tabs>
        <IconButton size="small" onClick={handleClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent>
        <Box component="form" onSubmit={handleSubmit} sx={{ mt: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {error && <Alert severity="error" sx={{ borderRadius: 1 }}>{error}</Alert>}

          <TextField
            label="账号"
            value={userAccount}
            onChange={(e) => setUserAccount(e.target.value)}
            required
            fullWidth
            size="small"
            autoFocus
            inputProps={{ minLength: 4 }}
          />

          <TextField
            label="密码"
            type="password"
            value={userPassword}
            onChange={(e) => setUserPassword(e.target.value)}
            required
            fullWidth
            size="small"
            inputProps={{ minLength: 8 }}
          />

          {tab === 1 && (
            <TextField
              label="确认密码"
              type="password"
              value={checkPassword}
              onChange={(e) => setCheckPassword(e.target.value)}
              required
              fullWidth
              size="small"
              inputProps={{ minLength: 8 }}
            />
          )}

          <Button
            type="submit"
            variant="contained"
            fullWidth
            disabled={submitting}
            className="gradient-btn"
            sx={{ mt: 1, p: '10px !important' }}
          >
            {submitting ? '处理中...' : tab === 0 ? '登 录' : '注 册'}
          </Button>

          {tab === 0 && (
            <Typography variant="caption" sx={{ textAlign: 'center', color: 'var(--text-muted)' }}>
              测试账号: user / 12345678
            </Typography>
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}
