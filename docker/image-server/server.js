const express = require("express");
const cors = require("cors");
const multer = require("multer");
const fs = require("fs");
const path = require("path");
const { v4: uuidv4 } = require("uuid");

const PORT = Number(process.env.PORT || 8080);
const STORAGE_ROOT = process.env.STORAGE_ROOT || "/data";
const API_KEY = process.env.IMAGE_SERVER_API_KEY || "";
const PUBLIC_BASE_URL = process.env.PUBLIC_BASE_URL || "http://localhost:8080";

const uploadsDir = path.join(STORAGE_ROOT, "uploads");
fs.mkdirSync(uploadsDir, { recursive: true });

const app = express();
app.use(cors());
app.use(express.json());

app.use("/uploads", express.static(uploadsDir));

const upload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: 8 * 1024 * 1024
  }
});

function requireApiKey(req, res, next) {
  if (!API_KEY) return next();

  const provided = req.header("x-api-key") || "";
  if (provided !== API_KEY) {
    return res.status(401).json({ error: "Invalid API key" });
  }
  next();
}

function extensionFromMime(mimeType) {
  switch ((mimeType || "").toLowerCase()) {
    case "image/jpeg":
    case "image/jpg":
      return "jpg";
    case "image/png":
      return "png";
    case "image/webp":
      return "webp";
    case "image/heic":
      return "heic";
    case "image/heif":
      return "heif";
    default:
      return "bin";
  }
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "pintxomatch-image-server" });
});

app.post("/api/images", requireApiKey, upload.single("file"), (req, res) => {
  if (!req.file || !req.file.buffer) {
    return res.status(400).json({ error: "Missing file" });
  }

  const imageId = `img_${Date.now()}_${uuidv4().replace(/-/g, "")}`;
  const ext = extensionFromMime(req.file.mimetype);
  const fileName = `${imageId}.${ext}`;
  const targetPath = path.join(uploadsDir, fileName);

  try {
    fs.writeFileSync(targetPath, req.file.buffer);
    const base = PUBLIC_BASE_URL.replace(/\/$/, "");
    const url = `${base}/uploads/${fileName}`;

    return res.status(201).json({
      imageId,
      fileName,
      url,
      contentType: req.file.mimetype || "application/octet-stream"
    });
  } catch (err) {
    return res.status(500).json({ error: err && err.message ? err.message : "Unable to store file" });
  }
});

app.delete("/api/images/:imageId", requireApiKey, (req, res) => {
  const imageId = req.params.imageId || "";
  if (!imageId) {
    return res.status(400).json({ error: "Missing imageId" });
  }

  try {
    const files = fs.readdirSync(uploadsDir);
    const match = files.find((f) => f.startsWith(`${imageId}.`));

    if (!match) {
      return res.status(404).json({ deleted: false, reason: "not_found" });
    }

    fs.unlinkSync(path.join(uploadsDir, match));
    return res.json({ deleted: true, imageId });
  } catch (err) {
    return res.status(500).json({ error: err && err.message ? err.message : "Unable to delete file" });
  }
});

app.listen(PORT, () => {
  console.log(`Image server listening on :${PORT}`);
});
