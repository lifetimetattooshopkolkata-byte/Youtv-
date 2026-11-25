import 'dotenv/config';
import cors from 'cors';
import express from 'express';
import rateLimit from 'express-rate-limit';
import helmet from 'helmet';
import { adminRouter } from './routes/admin.js';
import { distributionRouter } from './routes/distribution.js';
import { healthRouter } from './routes/health.js';
import { videosRouter } from './routes/videos.js';

export function createApp() {
  const app = express();
  const allowedOrigins = (process.env.ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  app.use(helmet());
  app.use(
    cors({
      origin: allowedOrigins.length > 0 ? allowedOrigins : false,
      credentials: false
    })
  );
  app.use(express.json({ limit: '1mb' }));

  app.use(
    rateLimit({
      windowMs: 60 * 1000,
      max: 120,
      standardHeaders: true,
      legacyHeaders: false
    })
  );

  app.use('/', healthRouter);
  app.use('/api/videos', videosRouter);
  app.use('/api/admin', adminRouter);
  app.use('/api/distribution', distributionRouter);

  return app;
}

const isMainModule =
  process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href;

if (isMainModule) {
  const port = Number(process.env.PORT ?? 8080);
  const app = createApp();
  app.listen(port, () => {
    console.log(`YouTV + ADMIT Cloud Run backend listening on port ${port}`);
  });
}
