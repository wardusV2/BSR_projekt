import React from 'react';
import { Menu } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const MenuItems = () => {
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <Menu
  theme="dark"
  mode="horizontal"
  selectedKeys={[window.location.pathname]}
>
  {isAuthenticated ? (
    <>
      <Menu.Item key="/landingpage">
        <Link to="/landingpage">LandingPage</Link>
      </Menu.Item>
      <Menu.Item key="/profile">
        <Link to="/profile">Profile</Link>
      </Menu.Item>
      <Menu.Item key="/recommendation">
        <Link to="/recommendation">Recommendation</Link>
      </Menu.Item>
      <Menu.Item key="/search">
        <Link to="/search">Search</Link>
      </Menu.Item>
      <Menu.Item key="username" disabled>
        {user?.nick || 'User'}
      </Menu.Item>
      <Menu.Item key="logout" onClick={handleLogout}>
        Logout
      </Menu.Item>
    </>
  ) : (
    <>
      <Menu.Item key="/landingpage">
        <Link to="/landingpage">LandingPage</Link>
      </Menu.Item>
      <Menu.Item key="/login">
        <Link to="/login">Login</Link>
      </Menu.Item>
      <Menu.Item key="/register">
        <Link to="/register">Register</Link>
      </Menu.Item>
    </>
  )}
</Menu>
  );
};

export default MenuItems;
