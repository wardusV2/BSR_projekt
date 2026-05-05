import React, { useState } from 'react';
import { Form, Input, Button, message, Typography, Modal } from 'antd';
import axios from 'axios';

const { Paragraph } = Typography;

function getCookie(name) {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop().split(';').shift();
}

const Register = () => {
  const [qrCode, setQrCode] = useState(null);
  const [secret, setSecret] = useState(null);
  const [isModalVisible, setIsModalVisible] = useState(false);

 const onFinish = async (values) => {
  try {
    // Krok 1: pobierz token CSRF
    const csrfRes = await axios.get('http://localhost:8080/csrf-token', {
      withCredentials: true,
    });
    const csrfToken = csrfRes.data.csrfToken;

    // Krok 2: wyślij dane rejestracyjne z CSRF tokenem
    const response = await axios.post(
      'http://localhost:8080/register',
      values,
      {
        withCredentials: true,
        headers: {
          'X-XSRF-TOKEN': csrfToken,
        },
      }
    );

    message.success(response.data.message);

    // Obsługa MFA (jeśli serwer ją zwraca)
    if (response.data.qrCode) {
      const qrCodeData = response.data.qrCode.replace(/^data:image\/png;base64,/, '');
      setQrCode(qrCodeData);
    }
    if (response.data.secret) {
      setSecret(response.data.secret);
    }
    if (response.data.qrCode || response.data.secret) {
      setIsModalVisible(true);
    }

  } catch (error) {
    console.error('Błąd rejestracji:', error);
    if (error.response && error.response.data) {
      message.error(error.response.data);
    } else {
      message.error('Błąd połączenia z serwerem.');
    }
  }
};
const handleClose = () => {
    setIsModalVisible(false);
  };
  return (
    <div style={{ maxWidth: 400, margin: '0 auto' }}>
      <h2>Rejestracja</h2>
      <Form layout="vertical" onFinish={onFinish}>
        <Form.Item
          label="Nick"
          name="nick"
          rules={[{ required: true, message: 'Podaj swój nick!' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Imię"
          name="name"
          rules={[{ required: true, message: 'Podaj swoje imię!' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Nazwisko"
          name="surname"
          rules={[{ required: true, message: 'Podaj swoje nazwisko!' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Email"
          name="email"
          rules={[
            { required: true, message: 'Podaj adres email!' },
            { type: 'email', message: 'Niepoprawny adres email!' }
          ]}
        >
          <Input />
        </Form.Item>

        <Form.Item
          label="Hasło"
          name="password"
          rules={[
            { required: true, message: 'Podaj hasło!' },
            {
              pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^\w\s]).{12,}$/,
              message: 'Hasło musi mieć min. 12 znaków, małą i dużą literę, cyfrę i znak specjalny.'
            }
          ]}
        >
          <Input.Password />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" block>
            Zarejestruj się
          </Button>
        </Form.Item>
      </Form>

     <Modal title="Kod QR" open={isModalVisible} onCancel={handleClose} footer={null}>
        {qrCode && (
          <div style={{ textAlign: 'center' }}>
            <Paragraph>Zeskanuj ten kod QR za pomocą aplikacji uwierzytelniającej:</Paragraph>
            <img src={`data:image/png;base64,${qrCode}`} alt="QR Code" style={{ maxWidth: '100%' }} />
            {secret && <Paragraph>Lub wprowadź ręcznie ten sekret: <strong>{secret}</strong></Paragraph>}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Register;