import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { 
  Row, 
  Col, 
  Card, 
  Typography, 
  Avatar, 
  Divider, 
  message, 
  Collapse, 
  Button,
  Space,
  Badge,
  Tag,
  Statistic,
  List,
  Empty,
  Skeleton
} from 'antd';
import { 
  UserOutlined, 
  MailOutlined, 
  VideoCameraOutlined, 
  HeartOutlined, 
  BellOutlined, 
  SettingOutlined,
  PlayCircleOutlined,
  EyeOutlined
} from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import './Profile.css';
import { useAuth } from '../auth/AuthContext';

const { Title, Text, Paragraph } = Typography;
const { Meta } = Card;
const { Panel } = Collapse;

const Profile = () => {
  const [user, setUser] = useState(null);
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { csrfToken } = useAuth();

  useEffect(() => {
  const fetchData = async () => {
    try {
      setLoading(true);
      
      const userRes = await axios.get('http://localhost:8080/check-auth', { withCredentials: true });
      const userData = userRes.data;
      setUser(userData);

      const videosRes = await axios.get(`http://localhost:8080/videosByUser/${userData.id}`, {
        withCredentials: true,
        headers: {
            'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
      });
      setVideos(videosRes.data);

    } catch (error) {
      message.error('Nie udało się pobrać danych');
    } finally {
      setLoading(false);
    }
  };

  fetchData();
}, []);


  const getThumbnail = (video) => {
    if (video.thumbnail) return video.thumbnail;
    if (video.url.includes('youtube.com')) {
      try {
        const videoId = new URLSearchParams(new URL(video.url).search).get('v');
        return `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
      } catch {
        return 'https://via.placeholder.com/320x180?text=Video';
      }
    }
    return video.url;
  };

  if (loading) {
    return (
      <div className="profile-container">
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={8}>
            <Card>
              <Skeleton avatar active paragraph={{ rows: 4 }} />
            </Card>
          </Col>
          <Col xs={24} lg={16}>
            <Card>
              <Skeleton active paragraph={{ rows: 8 }} />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }

  return (
    <div className="profile-container">
      {/* Header sekcja */}
      <Card className="profile-header" bodyStyle={{ padding: '24px 32px' }}>
        <Row align="middle" gutter={24}>
          <Col>
            <Badge 
              dot 
              status="success" 
              offset={[-8, 8]}
            >
              <Avatar 
                size={80} 
                icon={<UserOutlined />}
                style={{ 
                  backgroundColor: '#1890ff',
                  fontSize: '32px'
                }}
              >
                {user?.nick?.charAt(0)?.toUpperCase()}
              </Avatar>
            </Badge>
          </Col>
          <Col flex="auto">
            <Title level={2} style={{ margin: 0, color: '#262626' }}>
              {user?.nick}
            </Title>
            <Paragraph type="secondary" style={{ margin: '8px 0', fontSize: '16px' }}>
              <MailOutlined /> {user?.email}
            </Paragraph>
            <Space size="middle">
              <Tag color="blue" icon={<VideoCameraOutlined />}>
                {videos.length} filmów
              </Tag>
              <Tag color="green" icon={<HeartOutlined />}>
                Aktywny użytkownik
              </Tag>
            </Space>
          </Col>
          <Col>
            <Space direction="vertical" size="small">
              <Button type="primary" icon={<SettingOutlined />}>
                Edytuj profil
              </Button>
              <Button icon={<BellOutlined />}>
                Powiadomienia
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={[24, 24]} style={{ marginTop: 24 }}>
        {/* Lewa sekcja - statystyki i menu */}
        <Col xs={24} lg={8}>
          {/* Statystyki */}
          <Card title="Statystyki" className="stats-card">
            <Row gutter={16}>
              <Col span={12}>
                <Statistic
                  title="Filmy"
                  value={videos.length}
                  prefix={<VideoCameraOutlined />}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Col>
              <Col span={12}>
                <Statistic
                  title="Wyświetlenia"
                  value={1128}
                  prefix={<EyeOutlined />}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
            </Row>
          </Card>

          {/* Informacje osobiste */}
          <Card title="Informacje osobiste" style={{ marginTop: 16 }}>
            <List size="small">
              <List.Item>
                <Text strong>Imię:</Text>
                <Text style={{ marginLeft: 8 }}>
                  {user?.name || <Text type="secondary">Nie podano</Text>}
                </Text>
              </List.Item>
              <List.Item>
                <Text strong>Nazwisko:</Text>
                <Text style={{ marginLeft: 8 }}>
                  {user?.surname || <Text type="secondary">Nie podano</Text>}
                </Text>
              </List.Item>
              <List.Item>
                <Text strong>Status:</Text>
                <Badge status="success" text="Online" style={{ marginLeft: 8 }} />
              </List.Item>
            </List>
          </Card>

          {/* Menu */}
          <Card title="Menu" style={{ marginTop: 16 }}>
            <Collapse ghost>
              <Panel 
                header={
                  <Space>
                    <UserOutlined />
                    <Text strong>Zarządzaj kontem</Text>
                  </Space>
                } 
                key="1"
              >
                <List size="small">
                  <List.Item>
                    <Button type="link" style={{ padding: 0 }}>
                      Zmień hasło
                    </Button>
                  </List.Item>
                  <List.Item>
                    <Button type="link" style={{ padding: 0 }}>
                      Ustawienia prywatności
                    </Button>
                  </List.Item>
                </List>
              </Panel>
              
              <Panel 
                header={
                  <Space>
                    <HeartOutlined />
                    <Text strong>Rekomendacje</Text>
                  </Space>
                } 
                key="2"
              >
                <Paragraph type="secondary">
                  Personalizowane rekomendacje na podstawie Twoich preferencji
                </Paragraph>
                <Link to="/recommendation">
                  <Button type="primary" size="small">
                    Zobacz rekomendacje
                  </Button>
                </Link>
              </Panel>
              
              <Panel 
                header={
                  <Space>
                    <BellOutlined />
                    <Text strong>Subskrypcje</Text>
                  </Space>
                } 
                key="3"
              >
                <Paragraph type="secondary">
                  Zarządzaj swoimi subskrypcjami i powiadomieniami
                </Paragraph>
                <Button type="default" size="small">
                  Zarządzaj subskrypcjami
                </Button>
              </Panel>
            </Collapse>
          </Card>
        </Col>

        {/* Prawa sekcja - filmy */}
        <Col xs={24} lg={16}>
          <Card 
            title={
              <Space>
                <PlayCircleOutlined />
                <span>Twoje filmy</span>
                <Badge count={videos.length} style={{ backgroundColor: '#52c41a' }} />
              </Space>
            }
            extra={
              <Button type="primary" onClick={() => navigate('/addvideo')}>
                Dodaj nowy film
              </Button>
            }
          >
            {videos.length === 0 ? (
              <Empty 
                description="Nie masz jeszcze żadnych filmów"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Button type="primary" icon={<VideoCameraOutlined />} onClick={() => navigate('/addvideo')} >
                  Dodaj pierwszy film
                </Button>
              </Empty>
            ) : (
              <Row gutter={[16, 16]}>
                {videos.map(video => (
                  <Col xs={24} sm={12} xl={8} key={video.id}>
                    <Card
                      hoverable
                      className="video-card"
                      cover={
                        <div className="video-thumbnail">
                          <img
                            alt="thumbnail"
                            src={getThumbnail(video)}
                            style={{ 
                              height: 180, 
                              width: '100%',
                              objectFit: 'cover' 
                            }}
                          />
                          <div className="video-overlay">
                            <PlayCircleOutlined className="play-icon" />
                          </div>
                        </div>
                      }
                      onClick={() => navigate(`/video/${video.id}`)}
                      actions={[
                        <EyeOutlined key="views" />,
                        <HeartOutlined key="like" />,
                        <SettingOutlined key="settings" />
                      ]}
                    >
                      <Meta 
                        title={
                          <Text ellipsis={{ tooltip: video.title }}>
                            {video.title}
                          </Text>
                        }
                        description={
                          <Space direction="vertical" size="small">
                            <Text type="secondary">
                              <EyeOutlined /> 1.2k wyświetleń
                            </Text>
                            <Text type="secondary">
                              Dodano 2 dni temu
                            </Text>
                          </Space>
                        }
                      />
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
            
            {videos.length > 0 && (
              <div style={{ textAlign: 'center', marginTop: 24 }}>
                <Button type="default" size="large">
                  Zobacz wszystkie filmy ({videos.length + 8})
                </Button>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Profile;