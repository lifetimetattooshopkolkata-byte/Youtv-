import { Router } from 'express';
import { configuredDatabaseId, configuredProjectId } from '../config/firebase.js';
import { isTranscoderConfigured } from '../services/transcoder.js';

export const healthRouter = Router();

healthRouter.get('/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'youtv-admit-backend',
    projectId: configuredProjectId,
    databaseId: configuredDatabaseId,
    transcoderConfigured: isTranscoderConfigured()
  });
});
