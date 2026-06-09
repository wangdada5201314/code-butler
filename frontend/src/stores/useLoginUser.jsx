import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

const LoginUserContext = createContext(null);

const API_BASE = '/api';

/**
 * React Context Provider for login user state.
 * Wraps the app and provides login/logout functions and current user info.
 */
export function LoginUserProvider({ children }) {
  const [loginUser, setLoginUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Fetch current user from backend (via session cookie)
  const fetchLoginUser = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/user/get/login`, {
        method: 'GET',
        credentials: 'include',
      });
      const data = await res.json();
      if (data.code === 0 && data.data) {
        setLoginUser(data.data);
      } else {
        setLoginUser(null);
      }
    } catch {
      setLoginUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  // Check login on mount
  useEffect(() => {
    fetchLoginUser();
  }, [fetchLoginUser]);

  const login = useCallback(async (userAccount, userPassword) => {
    const res = await fetch(`${API_BASE}/user/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userAccount, userPassword }),
    });
    const data = await res.json();
    if (data.code !== 0) {
      throw new Error(data.message || '登录失败');
    }
    setLoginUser(data.data);
    return data.data;
  }, []);

  const register = useCallback(async (userAccount, userPassword, checkPassword) => {
    const res = await fetch(`${API_BASE}/user/register`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userAccount, userPassword, checkPassword }),
    });
    const data = await res.json();
    if (data.code !== 0) {
      throw new Error(data.message || '注册失败');
    }
    return data.data;
  }, []);

  const logout = useCallback(async () => {
    await fetch(`${API_BASE}/user/logout`, {
      method: 'POST',
      credentials: 'include',
    });
    setLoginUser(null);
  }, []);

  return (
    <LoginUserContext.Provider value={{ loginUser, loading, login, register, logout, fetchLoginUser }}>
      {children}
    </LoginUserContext.Provider>
  );
}

/**
 * Hook to consume login user context.
 */
export function useLoginUser() {
  const ctx = useContext(LoginUserContext);
  if (!ctx) {
    throw new Error('useLoginUser must be used within LoginUserProvider');
  }
  return ctx;
}
