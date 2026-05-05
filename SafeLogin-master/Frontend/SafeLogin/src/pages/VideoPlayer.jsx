import React, { useEffect, useState, useRef } from 'react'; // DODAJ useRef
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Input,
  Button,
  Avatar,
  Typography,
  Space,
  Divider,
  Empty,
  message,
  Row,
  Col,
  Badge,
  Tag,
  Tooltip,
  Skeleton
} from 'antd';
import {
  UserOutlined,
  SendOutlined,
  MessageOutlined,
  PlayCircleOutlined,
  EyeOutlined,
  CommentOutlined,
  HeartOutlined,
  ShareAltOutlined,
  SaveOutlined
} from '@ant-design/icons';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import axios from 'axios';
import './VideoPlayer.css';

const { TextArea } = Input;
const { Title, Text, Paragraph } = Typography;

const VideoPlayer = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [video, setVideo] = useState(null);
  const [recommended, setRecommended] = useState([]);
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [pageLoading, setPageLoading] = useState(true);
  const [isSubscribed, setIsSubscribed] = useState(false);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [stompClient, setStompClient] = useState(null);
  const [isConnected, setIsConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState('Rozłączono');
  const [isLiked, setIsLiked] = useState(false);
  const [likeLoading, setLikeLoading] = useState(false);
  
  // Referencje do kontroli zapisywania
  const hasSavedHistory = useRef(false);
  const videoRef = useRef(null);

  // Główne pobieranie danych
  useEffect(() => {
    const fetchData = async () => {
      setPageLoading(true);
      try {
        console.log("📥 Rozpoczynam pobieranie danych...");
        
        // Pobierz video
        const videoRes = await axios.get(`http://localhost:8080/getVideo/${id}`, { 
          withCredentials: true ,
        });
        console.log("✅ Video pobrane:", videoRes.data);
        setVideo(videoRes.data);
        
        // Pobierz użytkownika
        const userRes = await axios.get('http://localhost:8080/check-auth', { 
          withCredentials: true 
        });
        console.log("✅ Użytkownik pobrany:", userRes.data);
        setCurrentUser(userRes.data);
        
        // Pobierz komentarze
        const commentRes = await axios.get(`http://localhost:8080/getVideoComments/${id}`, { 
          withCredentials: true 
        });
        setComments(commentRes.data);
        
        // Pobierz rekomendacje
        const allVideos = await axios.get('http://localhost:8080/AllVideos', { 
          withCredentials: true ,
          headers : {
            "X-SERVICE-KEY": "SUPER_SECRET_SERVICE_KEY_123",
          }
        });
        setRecommended(allVideos.data.filter(v => v.id !== parseInt(id)));
        
        console.log("✅ Wszystkie dane pobrane");
        
      } catch (error) {
        console.error('❌ Błąd pobierania danych:', error);
        message.error('Błąd podczas ładowania danych');
      } finally {
        setPageLoading(false);
      }
    };
    
    fetchData();
  }, [id]);

  // AUTOMATYCZNY ZAPIS HISTORII przy załadowaniu komponentu
  useEffect(() => {
    const saveHistoryOnLoad = async () => {
      console.log("🔄 Sprawdzam warunki zapisu historii...");
      console.log("Video:", video?.id ? `ID: ${video.id}, Tytuł: ${video.title}` : "BRAK");
      console.log("User:", currentUser?.id ? `ID: ${currentUser.id}, Nick: ${currentUser.userNick || currentUser.nick}` : "BRAK");
      console.log("Już zapisano?:", hasSavedHistory.current);
      
      if (video?.id && currentUser?.id && !hasSavedHistory.current) {
        console.log("✅ Warunki spełnione - rozpoczynam zapis historii");
        
        try {
          // 1. Pobierz CSRF token
          console.log("🔄 Pobieram CSRF token...");
          const csrfRes = await axios.get("http://localhost:8080/csrf-token", { 
            withCredentials: true 
          });
          const csrfToken = csrfRes.data.csrfToken;
          console.log("✅ CSRF token:", csrfToken ? "otrzymany" : "brak");
          
          // 2. Wyślij request DO POPRAWNEGO ENDPOINTU: /api/history/add
          console.log("📤 Wysyłam do endpointu: /api/history/add");
          console.log("Dane:", {
            userId: currentUser.id,
            videoId: video.id,
            position: 0
          });
          
          const response = await axios.post(
            `http://localhost:8080/api/history/add`,
            {
              userId: currentUser.id,
              videoId: video.id,
              position: 0
            },
            {
              withCredentials: true,
              headers: { 
                "X-XSRF-TOKEN": csrfToken,
                "X-SERVICE-KEY": "SUPER_SECRET_SERVICE_KEY_123", // edpoint api/history/* jest zabezpieczony dla service,  X-SERVICE-KEY jest potrzebny do autoryzacji
                "Content-Type": "application/json"
              }
            }
          );
          
          console.log("✅ Historia ZAPISANA POMYŚLNIE!", response.data);
          hasSavedHistory.current = true;
          message.success("Dodano do historii oglądania!");
          
        } catch (error) {
          console.error("🔴 Błąd podczas zapisywania historii:", {
            message: error.message,
            status: error.response?.status,
            data: error.response?.data,
            url: error.config?.url
          });
          
          if (error.response?.status === 404) {
            console.log("❌ Endpoint /api/history/add nie znaleziony");
            message.error("Endpoint historii nie znaleziony");
          } else if (error.response?.status === 403) {
            console.log("❌ Brak uprawnień lub błędny CSRF token");
            message.error("Brak uprawnień do zapisu historii");
          } else {
            message.error("Błąd zapisu historii");
          }
        }
      } else {
        console.log("⏸️ Pomijam zapis historii:", {
          reason: !video?.id ? "Brak video" : 
                  !currentUser?.id ? "Brak użytkownika" : 
                  "Już zapisano"
        });
      }
    };
    
    // Opóźnij zapis o 1 sekundę, żeby wszystko się załadowało
    const timer = setTimeout(() => {
      saveHistoryOnLoad();
    }, 1000);
    
    return () => clearTimeout(timer);
  }, [video, currentUser]);

  // Reset flagi przy zmianie video
  useEffect(() => {
    console.log("🔄 Zmiana video - resetuję flagę zapisu");
    hasSavedHistory.current = false;
  }, [id]);

  // Funkcja do ręcznego zapisu historii
  const handleManualSaveHistory = async () => {
    console.log("🔄 Ręczny zapis historii...");
    
    if (!currentUser?.id || !video?.id) {
      message.warning("Brak danych do zapisu historii");
      return;
    }
    
    try {
      const csrfRes = await axios.get("http://localhost:8080/csrf-token", { 
        withCredentials: true 
      });
      
      const response = await axios.post(
        `http://localhost:8080/api/history/add`,
        {
          userId: currentUser.id,
          videoId: video.id,
          position: 0
        },
        {
          withCredentials: true,
          headers: { 
            "X-XSRF-TOKEN": csrfRes.data.csrfToken,
            "Content-Type": "application/json"
          }
        }
      );
      
      console.log("✅ Ręczny zapis udany:", response.data);
      message.success("Historia zapisana ręcznie!");
      hasSavedHistory.current = true;
      
    } catch (error) {
      console.error("❌ Ręczny zapis nieudany:", error);
      message.error("Nie udało się zapisać historii");
    }
  };

  // ... RESZTA KODU (WebSocket, komentarze, subskrypcje itp.) pozostaje bez zmian
  // Zachowaj cały pozostały kod taki jak był, tylko dodaj import useRef na górze

  // WebSocket - zachowujemy istniejący kod (bez zmian)
  useEffect(() => {
    if (!currentUser) {
      console.log('❌ Brak currentUser - nie inicjalizuję WebSocket');
      setConnectionStatus('Nie zalogowano');
      return;
    }

    console.log('🔍 Dane użytkownika:', currentUser);
    console.log('🚀 Rozpoczynam inicjalizację WebSocket...');
    setConnectionStatus('Łączenie...');
    
    const userNick = currentUser.userNick || currentUser.nick || currentUser.username;
    if (!userNick) {
      console.error('❌ Brak nick użytkownika:', currentUser);
      message.error('Błąd: brak nazwy użytkownika');
      return;
    }

    console.log('👤 Używam nicka:', userNick);

    let publicSub, privateSub;
    let isCleanedUp = false;

    const client = new Client({
      webSocketFactory: () => {
        console.log('🔌 Tworzę połączenie SockJS do: http://localhost:8080/ws');
        const sock = new SockJS('http://localhost:8080/ws');
        
        sock.onopen = () => {
          console.log('🟢 SockJS: onopen - połączenie otwarte');
        };
        
        sock.onclose = (e) => {
          console.log('🔴 SockJS: onclose - połączenie zamknięte', e);
        };
        
        sock.onerror = (e) => {
          console.error('🔴 SockJS: onerror - błąd połączenia', e);
        };
        
        return sock;
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: (frame) => {
        if (isCleanedUp) {
          console.log('⚠️ Połączenie nawiązane, ale komponent już został oczyszczony');
          return;
        }
        
        console.log('✅ STOMP: Połączono z WebSocket!', frame);
        setIsConnected(true);
        setConnectionStatus('Połączono');

        try {
          // Subskrypcja na publiczne wiadomości
          console.log('📡 Tworzę subskrypcję publiczną: /topic/messages');
          publicSub = client.subscribe('/topic/messages', message => {
            if (isCleanedUp) return;
            console.log('📨 Otrzymano publiczną wiadomość:', message.body);
            try {
              const body = JSON.parse(message.body);
              setChatMessages(prev => [...prev, body]);
            } catch (error) {
              console.error('❌ Błąd parsowania wiadomości:', error);
            }
          });

          // Subskrypcja na prywatne wiadomości
          const privateDestination = `/user/${userNick}/queue/private`;
          console.log('🔒 Tworzę subskrypcję prywatną:', privateDestination);
          privateSub = client.subscribe(privateDestination, message => {
            if (isCleanedUp) return;
            console.log('🔒 Otrzymano prywatną wiadomość:', message.body);
            try {
              const body = JSON.parse(message.body);
              setChatMessages(prev => [...prev, { 
                ...body, 
                content: `🔒 ${body.content}`,
                isPrivate: true 
              }]);
            } catch (error) {
              console.error('❌ Błąd parsowania prywatnej wiadomości:', error);
            }
          });

          console.log('✅ Wszystkie subskrypcje utworzone pomyślnie');
        } catch (error) {
          console.error('❌ Błąd tworzenia subskrypcji:', error);
        }
      },

      onDisconnect: (frame) => {
        console.log('❌ STOMP: Rozłączono z WebSocket', frame);
        if (!isCleanedUp) {
          setIsConnected(false);
          setConnectionStatus('Rozłączono');
        }
      },

      onStompError: (error) => {
        console.error('❌ STOMP: Błąd protokołu STOMP:', error);
        if (!isCleanedUp) {
          setIsConnected(false);
          setConnectionStatus('Błąd STOMP');
          message.error(`Błąd STOMP: ${error.headers?.message || error.command}`);
        }
      },

      onWebSocketError: (error) => {
        console.error('❌ WebSocket: Błąd połączenia WebSocket:', error);
        if (!isCleanedUp) {
          setIsConnected(false);
          setConnectionStatus('Błąd WebSocket');
        }
      },

      onWebSocketClose: (event) => {
        console.log('🔴 WebSocket: Połączenie zamknięte', {
          code: event.code,
          reason: event.reason,
          wasClean: event.wasClean
        });
      },

      debug: (str) => {
        console.log('🔧 STOMP Debug:', str);
      }
    });

    try {
      console.log('⚡ Aktywuję klienta STOMP...');
      client.activate();
      setStompClient(client);
      console.log('✅ Klient STOMP aktywowany');
    } catch (error) {
      console.error('❌ Błąd aktywacji klienta STOMP:', error);
      setConnectionStatus('Błąd aktywacji');
    }

    return () => {
      console.log('🧹 Rozpoczynam cleanup WebSocket...');
      isCleanedUp = true;
      
      if (publicSub) {
        try {
          console.log('🗑️ Anulowanie subskrypcji publicznej...');
          publicSub.unsubscribe();
        } catch (error) {
          console.warn('⚠️ Błąd anulowania publicznej subskrypcji:', error);
        }
      }
      
      if (privateSub) {
        try {
          console.log('🗑️ Anulowanie subskrypcji prywatnej...');
          privateSub.unsubscribe();
        } catch (error) {
          console.warn('⚠️ Błąd anulowania prywatnej subskrypcji:', error);
        }
      }
      
      if (client && client.active) {
        try {
          console.log('🔌 Dezaktywacja klienta STOMP...');
          client.deactivate();
        } catch (error) {
          console.warn('⚠️ Błąd dezaktywacji klienta:', error);
        }
      }
      
      setIsConnected(false);
      setConnectionStatus('Rozłączono');
      setStompClient(null);
      console.log('✅ Cleanup WebSocket zakończony');
    };
  }, [currentUser?.id]);

  // Sprawdzanie subskrypcji - bez zmian
  useEffect(() => {
    const checkSubscription = async () => {
      if (currentUser && video?.ownerId) {
        try {
          const subRes = await axios.get(`http://localhost:8080/getSubscriptions/${currentUser.id}`, {
            withCredentials: true,
            headers: {
            'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
          });
          const isSub = subRes.data.some(u => u.username === video.ownerNick);
          setIsSubscribed(isSub);
        } catch (err) {
          console.error('Błąd sprawdzania subskrypcji:', err);
        }
      }
    };
    checkSubscription();
  }, [currentUser, video]);

  // Obsługa komentarzy - bez zmian
  const handleAddComment = async () => {
    if (!currentUser) {
      message.warning('Musisz być zalogowany, aby dodać komentarz.');
      return;
    }
    if (!newComment.trim()) {
      message.warning('Komentarz nie może być pusty.');
      return;
    }
    setLoading(true);
    try {
      const csrfRes = await axios.get('http://localhost:8080/csrf-token', { withCredentials: true });
      await axios.post('http://localhost:8080/addComment', {
        userId: currentUser.id,
        videoId: parseInt(id),
        content: newComment,
      }, {
        withCredentials: true,
        headers: { 'X-XSRF-TOKEN': csrfRes.data.csrfToken }
      });
      setNewComment('');
      const newComm = await axios.get(`http://localhost:8080/getVideoComments/${id}`, { withCredentials: true });
      setComments(newComm.data);
      message.success('Komentarz został dodany!');
    } catch (error) {
      console.error('Błąd dodawania komentarza:', error);
      message.error(error.response?.status === 403 ? 'Brak autoryzacji lub niepoprawny CSRF token.' : 'Błąd podczas dodawania komentarza.');
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
  const checkIfLiked = async () => {
    if (!currentUser?.id || !video?.id) return;

    try {
      const res = await axios.get(
        `http://localhost:8080/users/${currentUser.id}/liked`,
        {
          withCredentials: true,
          headers: {
            'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
        }
      );

      const liked = res.data.some(v => v.id === video.id);
      setIsLiked(liked);
    } catch (err) {
      console.error('❌ Błąd sprawdzania like:', err);
    }
  };
  checkIfLiked();
}, [currentUser, video]);
  const handleLikeToggle = async () => {
  if (!currentUser) {
    message.warning('Musisz być zalogowany, aby polubić wideo');
    return;
  }

  setLikeLoading(true);

  try {
    const csrfRes = await axios.get(
      'http://localhost:8080/csrf-token',
      { withCredentials: true }
    );

    const url = isLiked
      ? `http://localhost:8080/users/${currentUser.id}/unlike/${video.id}`
      : `http://localhost:8080/users/${currentUser.id}/like/${video.id}`;

    await axios.post(
      url,
      {},
      {
        withCredentials: true,
        headers: {
          'X-XSRF-TOKEN': csrfRes.data.csrfToken,
          'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
        }
      }
    );

    setIsLiked(prev => !prev);
    message.success(isLiked ? 'Usunięto polubienie' : 'Polubiono wideo ❤️');
  } catch (err) {
    console.error('❌ Błąd like/unlike:', err);
    message.error('Nie udało się zapisać polubienia');
  } finally {
    setLikeLoading(false);
  }
};

  // Obsługa subskrypcji - bez zmian
  const handleSubscribe = async () => {
    try {
      const csrfRes = await axios.get('http://localhost:8080/csrf-token', { withCredentials: true });
      await axios.post(
        `http://localhost:8080/subscriber/${currentUser.id}/subscribeTarget/${video.ownerId}`,
        {},
        {
          withCredentials: true,
          headers: {
            'X-XSRF-TOKEN': csrfRes.data.csrfToken,
             'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
        }
      );
      setIsSubscribed(true);
      message.success('Zasubskrybowano użytkownika!');
    } catch (err) {
      console.error(err);
      message.error('Nie udało się zasubskrybować.');
    }
  };

  const handleUnsubscribe = async () => {
    try {
      const csrfRes = await axios.get('http://localhost:8080/csrf-token', { withCredentials: true });
      await axios.post(
        `http://localhost:8080/subscriber/${currentUser.id}/unsubscribe/${video.ownerId}`,
        {},
        {
          withCredentials: true,
          headers: {
            'X-XSRF-TOKEN': csrfRes.data.csrfToken,
             'X-SERVICE-KEY': 'SUPER_SECRET_SERVICE_KEY_123'
          }
        }
      );
      setIsSubscribed(false);
      message.success('Anulowano subskrypcję.');
    } catch (err) {
      console.error(err);
      message.error('Nie udało się anulować subskrypcji.');
    }
  };

  // Obsługa czatu - bez zmian
  const sendChatMessage = () => {
    if (!currentUser) {
      message.warning('Musisz być zalogowany, aby wysyłać wiadomości.');
      return;
    }
    
    if (!stompClient || !isConnected) {
      message.warning('Brak połączenia z czatem. Spróbuj ponownie za chwilę.');
      return;
    }
    
    if (!chatInput.trim()) {
      message.warning('Wiadomość nie może być pusty.');
      return;
    }

    const messageObj = {
      content: chatInput.trim(),
      sender: { 
        id: currentUser.id,
        nick: currentUser.userNick || currentUser.nick || currentUser.username
      },
      receiver: null,
      type: 'PUBLIC'
    };
    
    console.log('Wysyłanie wiadomości:', messageObj);
    
    try {
      stompClient.publish({ 
        destination: '/app/chat', 
        body: JSON.stringify(messageObj) 
      });
      setChatInput('');
    } catch (error) {
      console.error('Błąd wysyłania wiadomości:', error);
      message.error('Nie udało się wysłać wiadomości');
    }
  };

  // Pomocnicze funkcje dla YouTube - bez zmian
  const isYouTubeLink = video?.url?.includes('youtube.com');
  const getYouTubeEmbedUrl = url => {
    try {
      const videoId = new URLSearchParams(new URL(url).search).get('v');
      return `https://www.youtube.com/embed/${videoId}`;
    } catch {
      return null;
    }
  };
  const getYouTubeThumbnail = url => {
    try {
      const videoId = new URLSearchParams(new URL(url).search).get('v');
      return `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
    } catch {
      return null;
    }
  };

  // Renderowanie stanu ładowania - bez zmian
  if (pageLoading) {
    return (
      <div className="video-player-container">
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={16}>
            <Card className="video-section-card">
              <Skeleton.Image className="video-skeleton" active />
              <Skeleton active paragraph={{ rows: 3 }} />
            </Card>
            <Card className="comments-section-card" style={{ marginTop: 24 }}>
              <Skeleton active avatar paragraph={{ rows: 2 }} />
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card>
              <Skeleton active paragraph={{ rows: 4 }} />
            </Card>
          </Col>
        </Row>
      </div>
    );
  }

  if (!video) return <div>Błąd ładowania wideo</div>;

  return (
    <div className="video-player-container">
      <Row gutter={[24, 24]}>
        <Col xs={24} lg={16}>
          <Card className="video-section-card" bordered={false}>
            <div className="video-wrapper">
              {isYouTubeLink ? (
                <iframe
                  className="video-element"
                  src={getYouTubeEmbedUrl(video.url)}
                  title={video.title}
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                  onLoad={() => console.log("YouTube iframe załadowany")}
                />
              ) : (
                <video 
                  ref={videoRef}
                  controls
                  className="video-element"
                  onPlay={() => console.log("Video odtwarzane")}
                >
                  <source src={video.url} type="video/mp4" />
                  Twoja przeglądarka nie wspiera odtwarzacza wideo.
                </video>
              )}
            </div>
            <div className="video-info">
              <Title level={2} className="video-title">{video.title}</Title>
              <div className="video-meta">
                <Space size="large">
                  <Space><EyeOutlined /><Text>1,234 wyświetleń</Text></Space>
                  <Space><CommentOutlined /><Text>{comments.length} komentarzy</Text></Space>
                </Space>

                <Space className="video-actions">
                 <Tooltip title="Polub">
                <Button
                    icon={<HeartOutlined />}
                    size="large"
                    loading={likeLoading}
                    onClick={handleLikeToggle}
                    disabled={!currentUser}
                    danger={isLiked}
                    type={isLiked ? "primary" : "default"}
                  >
                    {isLiked ? "Polubione" : "Polub"}
                  </Button>
                </Tooltip>

                  <Tooltip title={isSubscribed ? "Anuluj subskrypcję" : "Subskrybuj kanał"}>
                    <Button
                      type={isSubscribed ? "default" : "primary"}
                      danger={isSubscribed}
                      size="large"
                      onClick={isSubscribed ? handleUnsubscribe : handleSubscribe}
                      disabled={!currentUser}
                    >
                      {isSubscribed ? "Anuluj subskrypcję" : "Subskrybuj"}
                    </Button>
                  </Tooltip>
                  <Tooltip title="Ręcznie zapisz historię">
                    <Button 
                      icon={<SaveOutlined />} 
                      size="large"
                      onClick={handleManualSaveHistory}
                      type="dashed"
                    >
                      Zapisz historię
                    </Button>
                  </Tooltip>
                  <Tooltip title="Udostępnij">
                    <Button icon={<ShareAltOutlined />} size="large">Udostępnij</Button>
                  </Tooltip>
                </Space>
              </div>
              <Divider />
              <Paragraph className="video-description">{video.description}</Paragraph>
            </div>
          </Card>

          <Card
            className="comments-section-card"
            title={(
              <Space>
                <MessageOutlined />
                <Title level={4} style={{ margin: 0 }}>Komentarze</Title>
                <Badge count={comments.length} showZero />
              </Space>
            )}
            bordered={false}
          >
            <Card size="small" className="add-comment-card">
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Space align="start" style={{ width: '100%' }}>
                  <Avatar icon={<UserOutlined />} src={currentUser?.avatar} size="large" />
                  <div style={{ flex: 1 }}>
                    <Text strong style={{ color: '#1890ff' }}>
                      {currentUser?.nick || 'Zaloguj się, aby komentować'}
                    </Text>
                    {currentUser && (
                      <Tag color="blue" size="small" style={{ marginLeft: 8 }}>Online</Tag>
                    )}
                  </div>
                </Space>
                <TextArea
                  value={newComment}
                  onChange={e => setNewComment(e.target.value)}
                  placeholder="Podziel się swoimi przemyśleniami..."
                  rows={4} maxLength={500} showCount
                  disabled={!currentUser}
                />
                <div style={{ textAlign: 'right' }}>
                  <Space>
                    <Button onClick={() => setNewComment('')} disabled={!newComment.trim()}>
                      Wyczyść
                    </Button>
                    <Button
                      type="primary"
                      icon={<SendOutlined />}
                      onClick={handleAddComment}
                      loading={loading}
                      disabled={!newComment.trim() || !currentUser}
                      className="submit-comment-btn"
                    >
                      Opublikuj komentarz
                    </Button>
                  </Space>
                </div>
              </Space>
            </Card>
            <Divider />
            {comments.length === 0 ? (
              <Empty
                description="Brak komentarzy"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Text type="secondary">Bądź pierwszy, który skomentuje to wideo!</Text>
              </Empty>
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {comments.map((comment, i) => (
                  <Card key={i} size="small" className="comment-card" hoverable>
                    <Space align="start" style={{ width: '100%' }}>
                      <Avatar icon={<UserOutlined />} src={comment.userAvatar} size="large" />
                      <div style={{ flex: 1 }}>
                        <Space direction="vertical" size="small" style={{ width: '100%' }}>
                          <Space wrap>
                            <Text strong className="comment-author">{comment.nick}</Text>
                            <Text type="secondary" className="comment-time">
                              {comment.createdAt ? new Date(comment.createdAt).toLocaleString('pl-PL') : 'Teraz'}
                            </Text>
                          </Space>
                          <Paragraph className="comment-content" style={{ margin: 0 }}>
                            {comment.content}
                          </Paragraph>
                        </Space>
                      </div>
                    </Space>
                  </Card>
                ))}
              </Space>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Card
              title={(
                <Space>
                  <MessageOutlined />
                  <Text>Czat na żywo</Text>
                  <Badge 
                    status={isConnected ? "processing" : "error"} 
                    text={connectionStatus} 
                  />
                  {currentUser && (
                    <Tag color="blue" size="small">
                      {currentUser.userNick || currentUser.nick || currentUser.username}
                    </Tag>
                  )}
                </Space>
              )}
              className="chat-section-card"
              bordered={false}
              extra={
                <Button 
                  size="small" 
                  onClick={() => {
                    console.log('🔄 Wymuszenie ponownego połączenia...');
                    if (stompClient) {
                      stompClient.deactivate();
                    }
                    setCurrentUser({...currentUser});
                  }}
                  disabled={isConnected}
                >
                  Połącz ponownie
                </Button>
              }
            >
              {!isConnected && (
                <div style={{ 
                  padding: 8, 
                  backgroundColor: '#fff7e6', 
                  border: '1px solid #ffd591',
                  borderRadius: 4,
                  marginBottom: 12,
                  textAlign: 'center'
                }}>
                  <Text type="warning">
                    {connectionStatus === 'Łączenie...' ? '🔄 Łączenie z czatem...' : '⚠️ Brak połączenia z czatem'}
                  </Text>
                </div>
              )}

              <div
                style={{
                  maxHeight: 300,
                  overflowY: 'auto',
                  padding: 8,
                  backgroundColor: '#f9f9f9',
                  borderRadius: 4,
                  marginBottom: 12
                }}
              >
                {chatMessages.length === 0 ? (
                  <Empty
                    description="Brak wiadomości"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    style={{ padding: '16px 0' }}
                  />
                ) : (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {chatMessages.map((msg, i) => (
                      <div
                        key={i}
                        style={{
                          backgroundColor: msg.sender?.nick === (currentUser?.userNick || currentUser?.nick || currentUser?.username)
                            ? '#e6f7ff' 
                            : msg.isPrivate 
                              ? '#fff7e6' 
                              : '#fff',
                          border: `1px solid ${msg.isPrivate ? '#ffd591' : '#f0f0f0'}`,
                          borderRadius: 8,
                          padding: 8
                        }}
                      >
                        <Space direction="vertical" size={2} style={{ width: '100%' }}>
                          <Space>
                            <Avatar size="small" icon={<UserOutlined />} />
                            <Text strong style={{ 
                              color: msg.sender?.nick === (currentUser?.userNick || currentUser?.nick || currentUser?.username) ? '#1890ff' : '#000' 
                            }}>
                              {msg.sender?.nick || 'Nieznany'}
                            </Text>
                            {msg.isPrivate && <Tag color="orange" size="small">Prywatne</Tag>}
                            <Text type="secondary" style={{ fontSize: 10 }}>
                              {msg.timestamp 
                                ? new Date(msg.timestamp).toLocaleTimeString('pl-PL') 
                                : new Date().toLocaleTimeString('pl-PL')
                              }
                            </Text>
                          </Space>
                          <Text>{msg.content}</Text>
                        </Space>
                      </div>
                    ))}
                  </Space>
                )}
              </div>

              <Input.TextArea
                value={chatInput}
                onChange={e => setChatInput(e.target.value)}
                onPressEnter={(e) => {
                  if (!e.shiftKey) {
                    e.preventDefault();
                    sendChatMessage();
                  }
                }}
                placeholder={currentUser 
                  ? isConnected
                    ? "Wpisz wiadomość i naciśnij Enter, aby wysłać (Shift+Enter dla nowej linii)" 
                    : "Łączenie z czatem..."
                  : "Zaloguj się, aby pisać na czacie"
                }
                rows={2}
                style={{ resize: 'none' }}
                disabled={!currentUser || !isConnected}
                maxLength={500}
                showCount
              />
              <div style={{ textAlign: 'right', marginTop: 8 }}>
                <Space>
                  <Button
                    onClick={() => setChatInput('')}
                    disabled={!chatInput.trim()}
                    size="small"
                  >
                    Wyczyść
                  </Button>
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    onClick={sendChatMessage}
                    disabled={!chatInput.trim() || !currentUser || !isConnected}
                    loading={connectionStatus === 'Łączenie...'}
                  >
                    Wyślij
                  </Button>
                </Space>
              </div>
            </Card>

            <Card
              title={(
                <Space>
                  <PlayCircleOutlined />
                  <Text>Polecane filmy</Text>
                </Space>
              )}
              className="recommended-section-card"
              bordered={false}
            >
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {recommended.map(v => (
                  <Card
                    key={v.id}
                    size="small"
                    className="recommended-video-card"
                    hoverable
                    onClick={() => navigate(`/video/${v.id}`)}
                    cover={(
                      <div className="recommended-thumbnail-wrapper">
                        <img
                          src={v.thumbnail || getYouTubeThumbnail(v.url) || v.url}
                          alt={v.title}
                          className="recommended-thumbnail"
                        />
                        <div className="play-overlay">
                          <PlayCircleOutlined />
                        </div>
                      </div>
                    )}
                  >
                    <Card.Meta
                      title={<Text className="recommended-title" ellipsis={{ tooltip: v.title }}>{v.title}</Text>}
                      description={(
                        <Space>
                          <Text type="secondary" className="recommended-meta">1,234 wyświetleń</Text>
                          <Text type="secondary">•</Text>
                          <Text type="secondary">2 dni temu</Text>
                        </Space>
                      )}
                    />
                  </Card>
                ))}
              </Space>
            </Card>
          </Space>
        </Col>
      </Row>
    </div>
  );
};

export default VideoPlayer;