// WbfMonitorGuard.jsx
import React, { useEffect, useState } from 'react';
import { Modal, Input, message, Spin } from 'antd';
import axios from 'axios';

const WbfMonitorGuard = ({ children }) => {
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [code, setCode] = useState('');

  useEffect(() => {
    checkAccess();
  }, []);

  const checkAccess = async () => {
    try {
      const response = await axios.get(
        'http://localhost:8080/wbfmonitor/access',
        { withCredentials: true }
      );

      if (response.data === true) {
        setAllowed(true);
      } else {
        setModalVisible(true);
      }
    } catch {
      message.error('Błąd autoryzacji');
    } finally {
      setLoading(false);
    }
  };

  const verify = async () => {
    try {
      const csrfRes = await axios.get(
        'http://localhost:8080/csrf-token',
        { withCredentials: true }
      );

      await axios.post(
        'http://localhost:8080/wbfmonitor/verify',
        { code },
        {
          withCredentials: true,
          headers: {
            'X-XSRF-TOKEN': csrfRes.data.csrfToken,
          },
        }
      );

      setAllowed(true);
      setModalVisible(false);

      message.success('Dostęp przyznany');
    } catch {
      message.error('Nieprawidłowy kod');
    }
  };

  if (loading) {
    return <Spin />;
  }

  return (
    <>
      <Modal
        title="Dodatkowa weryfikacja"
        open={modalVisible}
        closable={false}
        onOk={verify}
        okText="Potwierdź"
        cancelButtonProps={{ style: { display: 'none' } }}
      >
        <Input
                placeholder="Kod wysłany na e-mail"
                value={code}
                onChange={(e) => setCode(e.target.value)}
            />
      </Modal>

      {allowed && children}
    </>
  );
};

export default WbfMonitorGuard;