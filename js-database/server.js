import express from "express";
import cors from "cors";
import { watchHistoryDB } from "./db.js";

const app = express();
app.use(cors());
app.use(express.json());

const PORT = 3001;

/**
 * Zwraca historię oglądania dla użytkownika
 */
app.get("/history/:userId", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  res.json(data);
});

/**
 * Zwraca najczęściej oglądaną kategorię dla usera
 */
app.get("/history/:userId/favorite-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  const counter = {};

  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({
    userId: userId,
    mostWatchedCategory: Number(bestCategory)
  });
});

// ✅ najczęściej subskrybowana
app.get("/history/:userId/favorite-subscribed-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId && item.subscribe === true
  );

  const counter = {};
  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({ userId, mostSubscribedCategory: Number(bestCategory) });
});

// ✅ najczęściej polubiona
app.get("/history/:userId/favorite-liked-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId && item.like === true
  );

  const counter = {};
  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({ userId, mostLikedCategory: Number(bestCategory) });
});

// kategoria z najdłuższym łącznym czasem oglądania
app.get("/history/:userId/favorite-longest-watched-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  const timeCounter = {};
  data.forEach(item => {
    const watchTime = item.watch_time || 0;
    timeCounter[item.id_kategori] =
      (timeCounter[item.id_kategori] || 0) + watchTime;
  });

  let bestCategory = null;
  let maxTime = 0;

  for (const catId in timeCounter) {
    if (timeCounter[catId] > maxTime) {
      bestCategory = catId;
      maxTime = timeCounter[catId];
    }
  }

  res.json({ 
    userId, 
    mostLongestWatchedCategory: Number(bestCategory),
    totalWatchTime: maxTime
  });
});

//  kategoria z najwyższym współczynnikiem dokończenia
app.get("/history/:userId/favorite-completed-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  const stats = {};
  
  data.forEach(item => {
    if (!stats[item.id_kategori]) {
      stats[item.id_kategori] = {
        total: 0,
        completed: 0
      };
    }
    
    stats[item.id_kategori].total++;
    
    // Zakładamy, że jeśli watch_time >= 80% duration, to film został dokończony
    const completionRate = (item.watch_time || 0) / (item.duration || 1);
    if (completionRate >= 0.8) {
      stats[item.id_kategori].completed++;
    }
  });

  let bestCategory = null;
  let maxRate = 0;

  for (const catId in stats) {
    const rate = stats[catId].completed / stats[catId].total;
    if (rate > maxRate) {
      bestCategory = catId;
      maxRate = rate;
    }
  }

  res.json({ 
    userId, 
    mostCompletedCategory: Number(bestCategory),
    completionRate: maxRate
  });
});

// ===== START SERVER =====
app.listen(PORT, () => {
  console.log(`✅ JS DB działa na http://localhost:${PORT}`);
});