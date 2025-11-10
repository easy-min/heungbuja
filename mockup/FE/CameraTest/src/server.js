const express = require("express");
const multer = require("multer");
const path = require("path");
const fs = require("fs");

const app = express();

// 정적 파일 서빙
app.use(express.static(__dirname));

// 업로드 폴더 보장
const uploadDir = path.join(__dirname, "uploads");
fs.mkdirSync(uploadDir, { recursive: true });

// ✅ Multer 저장 방식 개선
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => {
    // ✅ originalname에서 segmentIndex와 frameIndex 추출
    // 클라이언트가 보내는 형식: seg01_frame_0.jpg
    const match = file.originalname.match(/seg(\d+)_frame_(\d+)\.jpg/);
    
    if (match) {
      const seg = match[1];      // "01"
      const frameIdx = match[2]; // "0"
      cb(null, `seg${seg}_${frameIdx.padStart(5, "0")}.jpg`);
    } else {
      // 매칭 실패 시 폴백
      console.warn(`⚠️  파일명 파싱 실패: ${file.originalname}`);
      cb(null, `unknown_${Date.now()}.jpg`);
    }
  },
});

const upload = multer({ storage });

// 업로드된 이미지 접근 가능하게
app.use("/uploads", express.static(uploadDir));

// ✅ 업로드 엔드포인트 개선
app.post("/upload", upload.array("frames"), (req, res) => {
  const { segmentIndex, frameCount, musicTimeStart, musicTimeEnd } = req.body;

  console.log(`\n🧩 === Segment ${segmentIndex} ===`);
  console.log(`  Expected frames: ${frameCount}`);
  console.log(`  Received frames: ${req.files?.length || 0}`);
  console.log(`  Music time: ${musicTimeStart}s ~ ${musicTimeEnd}s`);
  
  // ✅ 프레임 누락 체크
  if (req.files && parseInt(frameCount) !== req.files.length) {
    console.warn(`  ⚠️  Frame mismatch! Expected ${frameCount}, got ${req.files.length}`);
  }
  
  // ✅ 저장된 파일 상세 로그
  req.files?.forEach((f, idx) => {
    console.log(`  [${idx}] ${f.filename} (${(f.size / 1024).toFixed(1)}KB)`);
  });

  res.json({ 
    ok: true, 
    message: "Upload successful", 
    saved: req.files?.length || 0,
    segmentIndex: parseInt(segmentIndex)
  });
});

// ✅ 업로드된 세그먼트 목록 조회 API
app.get("/segments", (req, res) => {
  const files = fs.readdirSync(uploadDir);
  const segments = {};
  
  files.forEach(file => {
    const match = file.match(/seg(\d+)_(\d+)\.jpg/);
    if (match) {
      const seg = parseInt(match[1]);
      if (!segments[seg]) segments[seg] = [];
      segments[seg].push(file);
    }
  });
  
  res.json({ segments, total: Object.keys(segments).length });
});

const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
  console.log(`✅ Server running on http://localhost:${PORT}`);
  console.log(`📁 Upload directory: ${uploadDir}`);
});