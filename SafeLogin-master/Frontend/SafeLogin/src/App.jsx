import React from 'react';
import './App.css';
import { Layout, theme, Breadcrumb } from 'antd';
import { BrowserRouter as Router, Routes, Route, Navigate, Link } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import Profile from './pages/Profile';
import Recommendation from './pages/Recommendation';
import Search from './pages/Search';
import LandingPage from './pages/LandingPage';
import { AuthProvider, useAuth } from './auth/AuthContext';
import PrivateRoute from './auth/PrivateRoute';
import MenuItems from './components/MenuItems';
import VideoPlayer from './pages/VideoPlayer';
import AddVideo from './pages/AddVideo';
const { Header, Content, Footer } = Layout;

const AppContent = () => {
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const { isAuthenticated } = useAuth();

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <MenuItems />
      </Header>

      <div className="app-content-wrapper">
        <Breadcrumb className="app-breadcrumb">
          <Breadcrumb.Item>Strona</Breadcrumb.Item>
          <Breadcrumb.Item>Główna</Breadcrumb.Item>
        </Breadcrumb>

        <Layout className="app-inner-layout" style={{ background: colorBgContainer, borderRadius: borderRadiusLG }}>
          <Content className="app-content">
                    <Routes>
                      <Route path="/" element={
                          isAuthenticated ? (
                            <Navigate to="/landingpage" replace />
                          ) : (
                            <div>
                              <h1>Witamy na głównej stronie</h1>
                              <p>Jeśli posiadasz konto, <Link to="/login">zaloguj się</Link>.</p>
                              <p>W przeciwnym wypadku, <Link to="/register">zarejestruj się</Link>.</p>
                              <LandingPage />
                            </div>
                          )
                        } />
                        <Route path="/login" element={<Login />} />
                        <Route path="/register" element={<Register />} />
                        <Route path="/landingpage" element={<LandingPage />} />
                        <Route path="/home" element={<PrivateRoute><Home /></PrivateRoute>} />
                        <Route path="/profile" element={<PrivateRoute><Profile /></PrivateRoute>} />
                        <Route path="/recommendation" element={<PrivateRoute><Recommendation /></PrivateRoute>} />
                        <Route path="/search" element={<PrivateRoute><Search /></PrivateRoute>} />
                        <Route path="/video/:id" element={<PrivateRoute><VideoPlayer /></PrivateRoute>} />
                        <Route path="/addvideo" element={<AddVideo />} />
                      </Routes>
          </Content>
        </Layout>
      </div>

      <Footer className="app-footer">©{new Date().getFullYear()}</Footer>
    </Layout>
  );
};

const App = () => (
  <AuthProvider>
    <Router>
      <AppContent />
    </Router>
  </AuthProvider>
);

export default App;