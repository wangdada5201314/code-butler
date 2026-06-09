import React, { useState, useEffect } from 'react';
import { Container, Box, Grid, Typography, keyframes } from '@mui/material';
import { LoginUserProvider } from './stores/useLoginUser.jsx';
import Header from './components/Header.jsx';
import HealthCard from './components/HealthCard.jsx';
import ReviewPanel from './components/ReviewPanel.jsx';
import ChatPanel from './components/ChatPanel.jsx';
import DocsPanel from './components/DocsPanel.jsx';
import LoginModal from './components/LoginModal.jsx';

const float = keyframes`
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
`;

function AppInner() {
  const [darkMode, setDarkMode] = useState(() => {
    return localStorage.getItem('theme') === 'dark';
  });
  const [loginModalOpen, setLoginModalOpen] = useState(false);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', darkMode ? 'dark' : 'light');
    localStorage.setItem('theme', darkMode ? 'dark' : 'light');
  }, [darkMode]);

  // Listen for 401 auth errors to open login modal
  useEffect(() => {
    const handler = () => setLoginModalOpen(true);
    window.addEventListener('auth:required', handler);
    return () => window.removeEventListener('auth:required', handler);
  }, []);

  return (
    <Box sx={{ minHeight: '100vh', pb: 6 }}>
      <Header
        darkMode={darkMode}
        onToggleTheme={() => setDarkMode((p) => !p)}
        onOpenLogin={() => setLoginModalOpen(true)}
      />

      <LoginModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        darkMode={darkMode}
      />

      {/* Hero Section */}
      <Box
        sx={{
          position: 'relative',
          pt: 7, pb: 5,
          textAlign: 'center',
          overflow: 'hidden',
        }}
      >
        {/* Background glow */}
        <Box
          sx={{
            position: 'absolute',
            top: '-40%', left: '50%',
            width: 500, height: 500,
            transform: 'translateX(-50%)',
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(99,102,241,0.15) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        {/* Floating icon */}
        <Box
          sx={{
            display: 'inline-flex',
            width: 72, height: 72,
            borderRadius: 2.5,
            background: 'linear-gradient(135deg, #6366f1, #06b6d4)',
            alignItems: 'center', justifyContent: 'center',
            mb: 2.5,
            animation: `${float} 3s ease-in-out infinite`,
            boxShadow: '0 0 40px rgba(99,102,241,0.4)',
          }}
        >
          <Typography sx={{ fontSize: '2rem' }}>🤖</Typography>
        </Box>

        <Typography
          sx={{
            fontSize: { xs: '1.6rem', sm: '2.2rem' },
            fontWeight: 800,
            letterSpacing: '-0.03em',
            background: darkMode
              ? 'linear-gradient(135deg, #e0e7ff, #a5b4fc, #22d3ee)'
              : 'linear-gradient(135deg, #1e1b4b, #4338ca, #06b6d4)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          让 AI 审查你的每一行代码
        </Typography>

        <Typography
          sx={{
            mt: 1.5, maxWidth: 520, mx: 'auto',
            fontSize: '0.95rem',
            color: darkMode ? '#94a3b8' : '#64748b',
            lineHeight: 1.7,
          }}
        >
          基于 AgentScope 2.0 · 支持代码审查 / 智能问答 / 文档生成 · 安全可靠
        </Typography>
      </Box>

      {/* Feature Panels */}
      <Container maxWidth="xl">
        {/* Health status row */}
        <Box sx={{ mb: 2.5 }} className="animate-in" style={{ animationDelay: '0.1s' }}>
          <HealthCard darkMode={darkMode} />
        </Box>

        {/* Two-column layout for main features */}
        <Grid container spacing={2.5}>
          {/* Left column: Review */}
          <Grid item xs={12} lg={6} className="animate-in" sx={{ animationDelay: '0.2s' }}>
            <ReviewPanel darkMode={darkMode} />
          </Grid>

          {/* Right column: Chat */}
          <Grid item xs={12} lg={6} className="animate-in" sx={{ animationDelay: '0.3s' }}>
            <ChatPanel darkMode={darkMode} />
          </Grid>

          {/* Bottom: Docs */}
          <Grid item xs={12} className="animate-in" sx={{ animationDelay: '0.4s' }}>
            <DocsPanel darkMode={darkMode} />
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
}

export default function App() {
  return (
    <LoginUserProvider>
      <AppInner />
    </LoginUserProvider>
  );
}
