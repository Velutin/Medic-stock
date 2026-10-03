import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setUnauthorizedHandler } from '../api/client';

const AuthContext = createContext(null);

/** Session state: current user, login, logout. The session itself is an HttpOnly cookie set by the API. */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [expiredMessage, setExpiredMessage] = useState(null);

  useEffect(() => {
    api('/auth/session', { skipAuthRedirect: true })
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    setUnauthorizedHandler((error) => {
      setUser((current) => {
        if (current) setExpiredMessage(error.message);
        return null;
      });
    });
  }, []);

  const login = useCallback(async (email, password, rememberMe) => {
    const logged = await api('/auth/session', {
      method: 'POST',
      body: { email, password, rememberMe },
      skipAuthRedirect: true,
    });
    setExpiredMessage(null);
    setUser(logged);
    return logged;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api('/auth/session', { method: 'DELETE', skipAuthRedirect: true });
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout, setUser, expiredMessage, clearExpired: () => setExpiredMessage(null) }),
    [user, loading, login, logout, expiredMessage],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
