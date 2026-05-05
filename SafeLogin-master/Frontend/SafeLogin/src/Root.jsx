import React from 'react';
import App from './App';
import { AuthProvider } from './auth/AuthContext';

const Root = () => (
  <AuthProvider>
    <App />
  </AuthProvider>
);

export default Root;