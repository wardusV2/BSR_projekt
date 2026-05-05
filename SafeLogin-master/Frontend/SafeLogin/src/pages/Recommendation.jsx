import React, { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  message,
  Typography,
  Skeleton,
  Empty,
  Tag,
  Button,
  Space,
  Divider,
  Avatar,
  Statistic,
  Affix,
  BackTop,
  Tooltip
} from 'antd';
import {
  PlayCircleOutlined,
  EyeOutlined,
  ClockCircleOutlined,
  HeartOutlined,
  HeartFilled,
  ShareAltOutlined,
  FilterOutlined,
  ThunderboltOutlined,
  FireOutlined,
  StarOutlined,
  VerticalAlignTopOutlined,
  TrophyOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import './Recommendation.css';

const { Title, Text, Paragraph } = Typography;
const { Meta } = Card;

/* ===================== HELPERS ===================== */

const getRandomItems = (array, count) =>
  [...array].sort(() => Math.random() - 0.5).slice(0, count);

const getRandomViews = () =>
  Math.floor(Math.random() * 50000) + 1000;

const getRandomDuration = () => {
  const minutes = Math.floor(Math.random() * 20) + 1;
  const seconds = Math.floor(Math.random() * 60);
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
};

const getThumbnail = (video) => {
  if (video.thumbnail) return video.thumbnail;
  if (video.url?.includes('youtube.com')) {
    try {
      const videoId = new URLSearchParams(new URL(video.url).search).get('v');
      return `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
    } catch {
      return 'https://via.placeholder.com/320x180?text=Video';
    }
  }
  return 'https://via.placeholder.com/320x180?text=Video';
};

/* ===================== COMPONENT ===================== */

const Recommendation = () => {
  const [videos, setVideos] = useState([]);
  const [filteredVideos, setFilteredVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeFilter, setActiveFilter] = useState('all');
  const [likedVideos, setLikedVideos] = useState(new Set());
  const navigate = useNavigate();

  useEffect(() => {
  const fetchRecommendedVideos = async () => {
    try {
      setLoading(true);

      /* 1️⃣ AUTH */
      const authRes = await fetch('http://localhost:8080/check-auth', {
        credentials: 'include'
      });
      if (!authRes.ok) throw new Error('Nieautoryzowany użytkownik');
      const user = await authRes.json();

      /* 2️⃣ NAJNOWSZA REKOMENDACJA */
      const recRes = await fetch(
        `http://localhost:8080/recommendations/user/${user.id}/latest`,
        {
          credentials: 'include',
          headers: {
            'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
        }
      );

      if (recRes.status === 204) {
        setVideos([]);
        setFilteredVideos([]);
        return;
      }

      if (!recRes.ok) throw new Error('Brak rekomendacji');

      const latestRecommendation = await recRes.json();
      const category = latestRecommendation.category;

      /* 3️⃣ FILMY Z TEJ KATEGORII */
      const videoRes = await fetch(
        `http://localhost:8080/videos/category/${category}`,
        {
          credentials: 'include',
          headers: {
            'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
        }
      );

      if (!videoRes.ok) throw new Error('Brak filmów w kategorii');

      const videosByCategory = await videoRes.json();

      /* 4️⃣ LOSOWE 3 */
      const randomVideos = getRandomItems(videosByCategory, 3);

      setVideos(randomVideos);
      setFilteredVideos(randomVideos);

    } catch (err) {
      console.error(err);
      message.error(err.message || 'Błąd ładowania rekomendacji');
    } finally {
      setLoading(false);
    }
  };

  fetchRecommendedVideos();
}, []);


  /* ===================== FILTER ===================== */

  const handleFilter = (filter) => {
    setActiveFilter(filter);

    if (filter === 'all') {
      setFilteredVideos(videos);
    } else if (filter === 'popular') {
      setFilteredVideos(getRandomItems(videos, videos.length));
    } else if (filter === 'recent') {
      setFilteredVideos([...videos].reverse());
    }
  };

  const handleLike = (videoId, e) => {
    e.stopPropagation();
    setLikedVideos(prev => {
      const newSet = new Set(prev);
      if (newSet.has(videoId)) {
        newSet.delete(videoId);
        message.success('Usunięto z ulubionych');
      } else {
        newSet.add(videoId);
        message.success('Dodano do ulubionych');
      }
      return newSet;
    });
  };

  const handleShare = (video, e) => {
    e.stopPropagation();
    message.success(`Link do "${video.title}" skopiowano do schowka`);
  };

  const filterButtons = [
    { key: 'all', label: 'Wszystkie', icon: <ThunderboltOutlined /> },
    { key: 'popular', label: 'Popularne', icon: <FireOutlined /> },
    { key: 'recent', label: 'Najnowsze', icon: <ClockCircleOutlined /> }
  ];

  /* ===================== LOADING ===================== */

  if (loading) {
    return (
      <div className="recommendation-container">
        <div className="recommendation-header">
          <div className="header-content">
            <div className="title-section">
              <Title level={1} className="main-title">
                <ThunderboltOutlined className="title-icon" />
                Rekomendacje dla Ciebie
              </Title>
              <Paragraph className="subtitle">
                Ładowanie najlepszych filmów...
              </Paragraph>
            </div>
          </div>
        </div>

        <div className="videos-section">
          <Row gutter={[24, 24]}>
            {[1, 2, 3].map(i => (
              <Col xs={24} sm={12} md={8} key={i}>
                <Card>
                  <Skeleton.Image active style={{ width: '100%', height: 200 }} />
                  <Skeleton active paragraph={{ rows: 3 }} style={{ marginTop: 16 }} />
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      </div>
    );
  }

  /* ===================== RENDER ===================== */

  return (
    <div className="recommendation-container">
      {/* HEADER */}
      <div className="recommendation-header">
        <div className="header-content">
          <div className="title-section">
            <Title level={1} className="main-title">
              <ThunderboltOutlined className="title-icon" />
              Rekomendacje dla Ciebie
            </Title>
            <Paragraph className="subtitle">
              Wybrane na podstawie Twojej aktywności
            </Paragraph>
          </div>

          {filteredVideos.length > 0 && (
            <div className="stats-section">
              <Row gutter={24}>
                <Col span={12}>
                  <Statistic
                    title="Polecane filmy"
                    value={filteredVideos.length}
                    prefix={<StarOutlined />}
                    valueStyle={{ color: '#fff', fontSize: '24px' }}
                  />
                </Col>
                <Col span={12}>
                  <Statistic
                    title="Twoje polubienia"
                    value={likedVideos.size}
                    prefix={<HeartFilled />}
                    valueStyle={{ color: '#fff', fontSize: '24px' }}
                  />
                </Col>
              </Row>
            </div>
          )}
        </div>
      </div>

      {/* FILTERS */}
      <Affix offsetTop={0}>
        <Card className="filter-card" bordered={false}>
          <Space wrap size="middle" style={{ width: '100%', justifyContent: 'center' }}>
            <Space>
              <FilterOutlined style={{ fontSize: '16px', color: '#666' }} />
              <Text strong>Filtry:</Text>
            </Space>
            {filterButtons.map(btn => (
              <Button
                key={btn.key}
                type={activeFilter === btn.key ? 'primary' : 'default'}
                icon={btn.icon}
                onClick={() => handleFilter(btn.key)}
                className="filter-button"
                size="large"
              >
                {btn.label}
              </Button>
            ))}
          </Space>
        </Card>
      </Affix>

      {/* VIDEOS */}
      <div className="videos-section">
        {filteredVideos.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <Space direction="vertical" size="large">
                <Title level={4} type="secondary">Brak rekomendacji</Title>
                <Text type="secondary">
                  Zacznij oglądać filmy, aby otrzymać spersonalizowane rekomendacje
                </Text>
                <Button type="primary" size="large" onClick={() => navigate('/browse')}>
                  Przeglądaj filmy
                </Button>
              </Space>
            }
          />
        ) : (
          <Row gutter={[24, 24]}>
            {filteredVideos.map((video, index) => {
              const views = getRandomViews();
              const duration = getRandomDuration();
              const isLiked = likedVideos.has(video.id);
              const isTrending = index === 0;

              return (
                <Col xs={24} sm={12} lg={8} key={video.id}>
                  <Card
                    hoverable
                    className="video-card"
                    cover={
                      <div className="video-cover">
                        <img
                          src={getThumbnail(video)}
                          alt={video.title}
                          className="video-thumbnail"
                        />
                        <div className="video-overlay">
                          <PlayCircleOutlined className="play-button" />
                        </div>
                        <div className="video-duration">
                          <ClockCircleOutlined /> {duration}
                        </div>
                        {isTrending && (
                          <div className="trending-badge">
                            <FireOutlined /> Popularne
                          </div>
                        )}
                      </div>
                    }
                    actions={[
                      <Tooltip title={isLiked ? 'Usuń z ulubionych' : 'Dodaj do ulubionych'} key="like">
                        <span onClick={(e) => handleLike(video.id, e)}>
                          {isLiked ? (
                            <HeartFilled style={{ color: '#ff4d4f' }} />
                          ) : (
                            <HeartOutlined />
                          )}
                        </span>
                      </Tooltip>,
                      <Tooltip title="Udostępnij" key="share">
                        <ShareAltOutlined onClick={(e) => handleShare(video, e)} />
                      </Tooltip>,
                      <Tooltip title="Dodaj do playlisty" key="playlist">
                        <StarOutlined />
                      </Tooltip>
                    ]}
                    onClick={() => navigate(`/video/${video.id}`)}
                  >
                    <Meta
                      avatar={
                        <Avatar size={40} style={{ backgroundColor: '#667eea' }}>
                          {video.title?.charAt(0).toUpperCase()}
                        </Avatar>
                      }
                      title={
                        <Tooltip title={video.title}>
                          <div className="video-title">{video.title}</div>
                        </Tooltip>
                      }
                      description={
                        <Space direction="vertical" size="small" style={{ width: '100%' }}>
                          <Text type="secondary" className="channel-name">
                            Kanał Video
                          </Text>
                          <Space className="video-stats" size="large">
                            <Text type="secondary">
                              <EyeOutlined /> {views.toLocaleString()}
                            </Text>
                          </Space>
                          <div className="video-tags">
                            <Tag color="blue">{video.category}</Tag>
                            {isTrending && <Tag color="red">Trending</Tag>}
                          </div>
                        </Space>
                      }
                    />
                  </Card>
                </Col>
              );
            })}
          </Row>
        )}

        {filteredVideos.length >= 3 && (
          <div className="load-more-section">
            <Button
              type="primary"
              size="large"
              icon={<TrophyOutlined />}
              className="load-more-button"
            >
              Zobacz więcej rekomendacji
            </Button>
          </div>
        )}
      </div>

      <BackTop>
        <div className="back-to-top">
          <VerticalAlignTopOutlined />
        </div>
      </BackTop>
    </div>
  );
};

export default Recommendation;