import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { login as apiLogin, register as apiRegister, fetchMe } from '../api/auth';
import { getToken, setToken } from '../api/client';

const AuthContext = createContext({});

const USER_KEY = 'mimes_user';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setTokenState] = useState(null);
  const [loading, setLoading] = useState(true);

  // Восстановление сессии при старте
  useEffect(() => {
    (async () => {
      try {
        const [savedToken, savedUser] = await Promise.all([
          getToken(),
          AsyncStorage.getItem(USER_KEY),
        ]);
        if (savedToken && savedUser) {
          setTokenState(savedToken);
          setUser(JSON.parse(savedUser));
        }
      } catch (e) {
        console.warn('Restore session failed', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const login = async (username, password) => {
    const data = await apiLogin(username, password);
    await setToken(data.token);
    await AsyncStorage.setItem(USER_KEY, JSON.stringify(data.user));
    setTokenState(data.token);
    setUser(data.user);
    return data.user;
  };

  const register = async (username, password) => {
    const data = await apiRegister(username, password);
    await setToken(data.token);
    await AsyncStorage.setItem(USER_KEY, JSON.stringify(data.user));
    setTokenState(data.token);
    setUser(data.user);
    return data.user;
  };

  const logout = async () => {
    await setToken(null);
    await AsyncStorage.removeItem(USER_KEY);
    setTokenState(null);
    setUser(null);
  };

  // Проверяем токен на сервере один раз при загрузке
  const refreshUser = async () => {
    try {
      const me = await fetchMe();
      setUser((prev) => ({ ...prev, ...me }));
      await AsyncStorage.setItem(USER_KEY, JSON.stringify(me));
      return me;
    } catch {
      return null;
    }
  };

  const value = useMemo(
    () => ({ user, token, loading, login, register, logout, refreshUser }),
    [user, token, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
