import React, { useState } from 'react';
import { Form, Input, Button, message, Modal } from 'antd';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import axios from 'axios';

const Login = () => {
  const [form] = Form.useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [totpCode, setTotpCode] = useState('');
  const navigate = useNavigate();
  const { isAuthenticated, login, setIsAuthenticated, setUser } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/landingpage" />;
  }

  const onFinish = async (values) => {
    try {
      // Krok 1: pobierz token CSRF
      const csrfRes = await axios.get('http://localhost:8080/csrf-token', {
        withCredentials: true,
      });
      const csrfToken = csrfRes.data.csrfToken;

      // Krok 2: wyślij dane logowania z CSRF tokenem
      const response = await axios.post(
        'http://localhost:8080/login',
        values,
        {
          withCredentials: true,
          headers: {
            'X-XSRF-TOKEN': csrfToken,
          },
        }
      );

      const data = response.data;

      if (data.require2FA) {
        message.info(data.message || 'Wymagana weryfikacja 2FA');
        setIsModalVisible(true);
      } else {
        message.success(data.message || 'Zalogowano');
        login(data.user || {});
        navigate('/landingpage');
      }
    } catch (error) {
      console.error('Błąd logowania:', error);
      if (error.response && error.response.data) {
        message.error(error.response.data);
      } else {
        message.error('Błąd połączenia z serwerem.');
      }
    }
  };

  const handleTotpSubmit = async () => {
    try {
      // Pobierz token CSRF
      const csrfRes = await axios.get('http://localhost:8080/csrf-token', {
        withCredentials: true,
      });
      const csrfToken = csrfRes.data.csrfToken;

      const response = await axios.post(
        'http://localhost:8080/2fa/verify',
        { code: parseInt(totpCode, 10) },
        {
          withCredentials: true,
          headers: {
            'X-XSRF-TOKEN': csrfToken,
          },
        }
      );

      message.success(response.data.message || 'Weryfikacja zakończona');
      setIsModalVisible(false);
      setIsAuthenticated(true);

      const userResponse = await axios.get('http://localhost:8080/current-user', {
        withCredentials: true,
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
      });

      if (userResponse.status === 200) {
        setUser(userResponse.data);
      } else {
        setUser(null);
      }

      navigate('/home');
    } catch (error) {
      console.error('Błąd weryfikacji TOTP:', error);
      if (error.response && error.response.data) {
        message.error(error.response.data);
      } else {
        message.error('Błąd połączenia przy weryfikacji TOTP');
      }
    }
  };

  return (
    <div style={{ maxWidth: 400, margin: '0 auto' }}>
      <h2>Logowanie</h2>
      <Form layout="vertical" form={form} onFinish={onFinish}>
        <Form.Item
          label="Email"
          name="email"
          rules={[{ required: true, message: 'Podaj email!' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Hasło"
          name="password"
          rules={[{ required: true, message: 'Podaj hasło!' }]}
        >
          <Input.Password />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" block>
            Zaloguj się
          </Button>
        </Form.Item>
      </Form>

      <Modal
        title="Weryfikacja dwuskładnikowa"
        open={isModalVisible}
        onOk={handleTotpSubmit}
        onCancel={() => setIsModalVisible(false)}
        okText="Potwierdź"
        cancelText="Anuluj"
      >
        <Input
          placeholder="Wpisz kod TOTP"
          value={totpCode}
          onChange={(e) => setTotpCode(e.target.value)}
          maxLength={20}
        />
      </Modal>
    </div>
  );
};

export default Login;
