import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { 
  Row, 
  Col, 
  Card, 
  Typography, 
  message, 
  Skeleton, 
  Empty, 
  Button,
  Space,
  Tag,
  Avatar,
  Carousel,
  Divider,
  Input,
  Select,
  Affix
} from 'antd';
import { 
  PlayCircleOutlined,
  EyeOutlined,
  ClockCircleOutlined,
  FireOutlined,
  TrophyOutlined,
  SearchOutlined,
  FilterOutlined,
  HeartOutlined,
  UserOutlined
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import './LandingPage.css';

const { Title, Text, Paragraph } = Typography;
const { Meta } = Card;
const { Search } = Input;
const { Option } = Select;

const LandingPage = () => {
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('latest');
  const [filteredVideos, setFilteredVideos] = useState([]);
  const [featuredVideos, setFeaturedVideos] = useState([]);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchVideos = async () => {
        setLoading(true);
        try {
          // Krok 1: pobierz token CSRF
          const csrfRes = await axios.get('http://localhost:8080/csrf-token', {
            withCredentials: true,
          });
          const csrfToken = csrfRes.data.csrfToken;

          // Krok 2: pobierz filmy z tokenem CSRF
          const response = await axios.get('http://localhost:8080/AllVideos', {
            withCredentials: true,
            headers: {
              'X-XSRF-TOKEN': csrfToken,
              "X-SERVICE-KEY": "SUPER_SECRET_SERVICE_KEY_123",
            },
          });

          const data = response.data;
          setVideos(data);
          setFilteredVideos(data);
          setFeaturedVideos(data.slice(0, 3));
        } catch (error) {
          console.error(error);
          message.error('Błąd podczas pobierania filmów');
        } finally {
          setLoading(false);
        }
      };

    fetchVideos();
  }, []);

  useEffect(() => {
    let filtered = [...videos];

    // Search filter
    if (searchTerm) {
      filtered = filtered.filter(video =>
        video.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (video.description && video.description.toLowerCase().includes(searchTerm.toLowerCase()))
      );
    }

    // Sort
    switch (sortBy) {
      case 'popular':
        filtered.sort((a, b) => (b.views || 0) - (a.views || 0));
        break;
      case 'latest':
        filtered.sort((a, b) => new Date(b.createdAt || Date.now()) - new Date(a.createdAt || Date.now()));
        break;
      case 'title':
        filtered.sort((a, b) => a.title.localeCompare(b.title));
        break;
      default:
        break;
    }

    setFilteredVideos(filtered);
  }, [searchTerm, sortBy, videos]);

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

  const formatViews = (views) => {
    if (!views) return '0 wyświetleń';
    if (views >= 1000000) return `${(views / 1000000).toFixed(1)}M wyświetleń`;
    if (views >= 1000) return `${(views / 1000).toFixed(1)}K wyświetleń`;
    return `${views} wyświetleń`;
  };

  const getRandomViews = () => Math.floor(Math.random() * 10000) + 100;
  const getRandomAuthor = () => {
    const authors = ['TechGuru', 'VideoMaster', 'Creator Pro', 'StreamKing', 'ContentMaker'];
    return authors[Math.floor(Math.random() * authors.length)];
  };

  if (loading) {
    return (
      <div className="landing-container">
        <Skeleton.Input style={{ width: 200, height: 40, marginBottom: 24 }} active />
        <Row gutter={[24, 24]}>
          {Array.from({ length: 8 }).map((_, index) => (
            <Col xs={24} sm={12} md={8} lg={6} key={index}>
              <Card>
                <Skeleton.Image style={{ width: '100%', height: 180 }} />
                <Skeleton active paragraph={{ rows: 2 }} />
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    );
  }

  return (
    <div className="landing-container">
      {/* Hero Section */}
      <div className="hero-section">
        <div className="hero-content">
          <Title level={1} className="hero-title">
            Odkryj niesamowite <span className="gradient-text">filmy</span>
          </Title>
          <Paragraph className="hero-description">
            Platforma z najlepszymi filmami i treściami video. Oglądaj, dziel się i twórz wspólnie z nami!
          </Paragraph>
          <Space size="large">
            <Button 
              type="primary" 
              size="large" 
              icon={<PlayCircleOutlined />}
              className="hero-button"
              onClick={() => videos.length > 0 && navigate(`/video/${videos[0].id}`)}
            >
              Zacznij oglądać
            </Button>
            <Button 
              size="large" 
              icon={<TrophyOutlined />}
              className="hero-button-secondary"
            >
              Popularne dziś
            </Button>
          </Space>
        </div>
        <div className="hero-stats">
          <Row gutter={24}>
            <Col span={8}>
              <div className="stat-item">
                <Title level={3} style={{ color: '#1890ff', margin: 0 }}>
                  {videos.length}+
                </Title>
                <Text>Filmów</Text>
              </div>
            </Col>
            <Col span={8}>
              <div className="stat-item">
                <Title level={3} style={{ color: '#52c41a', margin: 0 }}>
                  10K+
                </Title>
                <Text>Wyświetleń</Text>
              </div>
            </Col>
            <Col span={8}>
              <div className="stat-item">
                <Title level={3} style={{ color: '#faad14', margin: 0 }}>
                  500+
                </Title>
                <Text>Twórców</Text>
              </div>
            </Col>
          </Row>
        </div>
      </div>

      {/* Featured Videos Carousel */}
      {featuredVideos.length > 0 && (
        <div className="featured-section">
          <Title level={2} className="section-title">
            <FireOutlined style={{ color: '#ff4d4f' }} /> Polecane
          </Title>
          <Carousel autoplay className="featured-carousel">
            {featuredVideos.map(video => (
              <div key={video.id} className="featured-slide">
                <Card
                  hoverable
                  className="featured-card"
                  cover={
                    <div className="featured-thumbnail">
                      <img
                        alt={video.title}
                        src={getThumbnail(video)}
                      />
                      <div className="featured-overlay">
                        <Button 
                          type="primary" 
                          size="large"
                          icon={<PlayCircleOutlined />}
                          onClick={() => navigate(`/video/${video.id}`)}
                        >
                          Oglądaj teraz
                        </Button>
                      </div>
                    </div>
                  }
                >
                  <Meta
                    title={<Title level={4}>{video.title}</Title>}
                    description={
                      <Space direction="vertical" size="small">
                        <Text>{video.description || 'Świetny film do obejrzenia!'}</Text>
                        <Space>
                          <Tag color="gold">Polecane</Tag>
                          <Tag color="blue">{formatViews(getRandomViews())}</Tag>
                        </Space>
                      </Space>
                    }
                  />
                </Card>
              </div>
            ))}
          </Carousel>
        </div>
      )}

      {/* Search and Filter Section */}
      <Affix offsetTop={20}>
        <Card className="search-filter-card">
          <Row gutter={16} align="middle">
            <Col xs={24} sm={12} md={14}>
              <Search
                placeholder="Szukaj filmów..."
                size="large"
                prefix={<SearchOutlined />}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="search-input"
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Select
                size="large"
                value={sortBy}
                onChange={setSortBy}
                style={{ width: '100%' }}
                prefix={<FilterOutlined />}
              >
                <Option value="latest">
                  <ClockCircleOutlined /> Najnowsze
                </Option>
                <Option value="popular">
                  <FireOutlined /> Popularne
                </Option>
                <Option value="title">
                  <FilterOutlined /> Alfabetycznie
                </Option>
              </Select>
            </Col>
            <Col xs={24} sm={24} md={4}>
              <Text type="secondary">
                {filteredVideos.length} filmów
              </Text>
            </Col>
          </Row>
        </Card>
      </Affix>

      <Divider />

      {/* Video Grid */}
      <div className="videos-section">
        <Title level={2} className="section-title">
          Wszystkie filmy
        </Title>
        
        {filteredVideos.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Nie znaleziono filmów"
            style={{ padding: '60px 0' }}
          >
            <Button type="primary" onClick={() => {
              setSearchTerm('');
              setSortBy('latest');
            }}>
              Wyczyść filtry
            </Button>
          </Empty>
        ) : (
          <Row gutter={[24, 24]} className="video-grid">
            {filteredVideos.map(video => (
              <Col xs={24} sm={12} md={8} lg={6} key={video.id}>
                <Card
                  hoverable
                  className="video-card"
                  cover={
                    <div className="video-thumbnail">
                      <img
                        alt={video.title}
                        src={getThumbnail(video)}
                      />
                      <div className="video-overlay">
                        <PlayCircleOutlined className="play-icon" />
                      </div>
                      <div className="video-duration">
                        {Math.floor(Math.random() * 10) + 1}:{Math.floor(Math.random() * 60).toString().padStart(2, '0')}
                      </div>
                    </div>
                  }
                  onClick={() => navigate(`/video/${video.id}`)}
                  actions={[
                    <EyeOutlined key="views" />,
                    <HeartOutlined key="like" />,
                    <Button type="text" size="small">Dodaj do listy</Button>
                  ]}
                >
                  <Meta
                    avatar={
                      <Avatar icon={<UserOutlined />} style={{ backgroundColor: '#1890ff' }}>
                        {getRandomAuthor().charAt(0)}
                      </Avatar>
                    }
                    title={
                      <Text ellipsis={{ tooltip: video.title }} strong>
                        {video.title}
                      </Text>
                    }
                    description={
                      <Space direction="vertical" size="small" style={{ width: '100%' }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {getRandomAuthor()}
                        </Text>
                        <Space>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            <EyeOutlined /> {formatViews(getRandomViews())}
                          </Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            <ClockCircleOutlined /> {Math.floor(Math.random() * 7) + 1} dni temu
                          </Text>
                        </Space>
                        <Space wrap>
                          <Tag color="processing" size="small">HD</Tag>
                          <Tag color="success" size="small">Nowe</Tag>
                        </Space>
                      </Space>
                    }
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </div>

      {/* Load More Section */}
      {filteredVideos.length > 0 && (
        <div style={{ textAlign: 'center', marginTop: 48 }}>
          <Button 
            type="default" 
            size="large"
            style={{ minWidth: 200 }}
          >
            Załaduj więcej filmów
          </Button>
        </div>
      )}
    </div>
  );
};

export default LandingPage;