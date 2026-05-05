import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [csrfToken, setCsrfToken] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);

  // Pobierz CSRF token przy starcie aplikacji
  useEffect(() => {
    fetch('http://localhost:8080/csrf-token', {
      credentials: 'include',
    })
      .then(res => {
        if (!res.ok) throw new Error('Failed to fetch CSRF token');
        return res.json();
      })
      .then(data => {
      setCsrfToken(data.csrfToken);
      })
      .catch(() => setCsrfToken(null));
  }, []);

  // Sprawdź czy użytkownik jest już zalogowany (jeśli token jest)
  useEffect(() => {
    if (!csrfToken) return;

    fetch('http://localhost:8080/check-auth', {
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': csrfToken },
    })
      .then(res => {
        if (res.ok) {
          setIsAuthenticated(true);
          return fetch('http://localhost:8080/current-user', {
            credentials: 'include',
            headers: { 'X-XSRF-TOKEN': csrfToken },
          });
        } else {
          setIsAuthenticated(false);
          setUser(null);
          throw new Error('Not authenticated');
        }
      })
      .then(res => res.json())
      .then(data => setUser(data))
      .catch(() => {
        setIsAuthenticated(false);
        setUser(null);
      });
  }, [csrfToken]);

  // Funkcja login, zwraca odpowiedź backendu (np. require2FA)
  const login = async (email, password) => {
    console.log(csrfToken);
    if (!csrfToken) throw new Error('CSRF token not loaded');

    const response = await fetch('http://localhost:8080/login', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || 'Login failed');
    }

    const data = await response.json();

    // Jeśli nie wymaga 2FA, zaloguj od razu
    if (!data.require2FA) {
      setIsAuthenticated(true);
      setUser(data.user || null);
    }

    return data; // zwróć dane (np. require2FA)
  };

  const logout = () => {
    if (!csrfToken) {
      setIsAuthenticated(false);
      setUser(null);
      return;
    }
    fetch('http://localhost:8080/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': csrfToken },
    }).then(() => {
      setIsAuthenticated(false);
      setUser(null);
    });
  };

  return (
    <AuthContext.Provider
      value={{
        csrfToken,
        isAuthenticated,
        user,
        login,
        logout,
        setIsAuthenticated,
        setUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
