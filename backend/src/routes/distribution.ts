import { Router } from 'express';
import { z } from 'zod';
import {
  type AuthenticatedRequest,
  requireAuthenticatedUser,
  requireVerifiedOwner
} from '../middleware/auth.js';
import { DistributionManager } from '../services/distributionManager.js';

export const distributionRouter = Router();
const distributionManager = new DistributionManager();

const buildTriggerSchema = z.object({
  versionName: z.string().trim().min(1).max(32).regex(/^[a-zA-Z0-9._-]+$/),
  versionCode: z.number().int().min(1).max(2_000_000_000),
  buildType: z.enum(['release', 'debug']).default('release'),
  channel: z.enum(['production', 'beta']).default('production'),
  releaseNotes: z.string().trim().max(1000).optional()
});

const webhookReadySchema = z.object({
  releaseId: z.string().trim().min(4).max(128).regex(/^[a-zA-Z0-9_-]+$/),
  sha256: z.string().trim().regex(/^[a-fA-F0-9]{64}$/),
  fileSizeBytes: z.number().int().positive().max(1024 * 1024 * 1024),
  gcsObjectPath: z.string().trim().max(300).optional(),
  hostingPath: z.string().trim().max(300).optional()
});

distributionRouter.get('/releases', requireAuthenticatedUser, async (_req, res) => {
  const releases = await distributionManager.listReleases(20);
  res.json({
    ...distributionManager.getStatusSummary(),
    releases
  });
});

distributionRouter.get('/releases/:releaseId/download', requireAuthenticatedUser, async (req, res) => {
  const rawReleaseId = req.params.releaseId;
  const releaseId = (Array.isArray(rawReleaseId) ? rawReleaseId[0] : rawReleaseId)?.trim();
  if (!releaseId || !/^[a-zA-Z0-9_-]+$/.test(releaseId)) {
    res.status(400).json({ error: 'Invalid releaseId' });
    return;
  }

  try {
    const result = await distributionManager.resolveDirectApkDownload(releaseId);
    if (!result.downloadUrl) {
      res.status(409).json(result);
      return;
    }
    if (req.query.format === 'redirect') {
      res.redirect(302, result.downloadUrl);
      return;
    }
    res.json(result);
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Release not found' });
  }
});

distributionRouter.post(
  '/build',
  requireAuthenticatedUser,
  requireVerifiedOwner,
  async (req: AuthenticatedRequest, res) => {
    const parsed = buildTriggerSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid build trigger payload', details: parsed.error.flatten() });
      return;
    }

    const result = await distributionManager.triggerApkBuild(parsed.data, req.user!.uid);
    res.status(result.setupRequired ? 202 : 201).json(result);
  }
);

distributionRouter.post(
  '/releases/:releaseId/upload-url',
  requireAuthenticatedUser,
  requireVerifiedOwner,
  async (req: AuthenticatedRequest, res) => {
    const rawReleaseId = req.params.releaseId;
    const releaseId = (Array.isArray(rawReleaseId) ? rawReleaseId[0] : rawReleaseId)?.trim();
    if (!releaseId || !/^[a-zA-Z0-9_-]+$/.test(releaseId)) {
      res.status(400).json({ error: 'Invalid releaseId' });
      return;
    }

    try {
      const result = await distributionManager.generateSignedApkUploadUrl(releaseId, req.user!.uid);
      res.json(result);
    } catch (err) {
      res.status(400).json({ error: err instanceof Error ? err.message : 'Failed to issue upload URL' });
    }
  }
);

distributionRouter.post('/webhook', async (req, res) => {
  const secretHeader = req.header('x-distribution-secret');
  if (!distributionManager.verifyWebhookSecret(secretHeader)) {
    res.status(401).json({ error: 'Invalid distribution webhook secret' });
    return;
  }

  const parsed = webhookReadySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid webhook payload', details: parsed.error.flatten() });
    return;
  }

  await distributionManager.markReleaseReady(parsed.data);
  res.json({ ok: true, releaseId: parsed.data.releaseId, status: 'READY' });
});
