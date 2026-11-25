import { Router } from 'express';
import { z } from 'zod';
import { adminDb } from '../config/firebase.js';
import { type AuthenticatedRequest, requireAuthenticatedUser } from '../middleware/auth.js';
import { queueVideoTranscode } from '../services/transcoder.js';

export const videosRouter = Router();

const transcodeRequestSchema = z.object({
  videoId: z.string().min(4).max(128).regex(/^[a-zA-Z0-9_-]+$/),
  fileName: z.string().min(1).max(180),
  mimeType: z.enum(['video/mp4', 'video/webm', 'video/quicktime']),
  fileSizeBytes: z.number().int().nonnegative().max(10 * 1024 * 1024 * 1024)
});

videosRouter.post('/transcode', requireAuthenticatedUser, async (req: AuthenticatedRequest, res) => {
  const parsed = transcodeRequestSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid transcode payload', details: parsed.error.flatten() });
    return;
  }

  const uid = req.user!.uid;
  const userSnap = await adminDb.collection('users').doc(uid).get();
  const userData = userSnap.data();
  if (userData && (userData.status === 'suspended' || userData.status === 'blocked' || userData.uploadDisabled === true)) {
    res.status(403).json({ error: 'Account uploads are disabled by moderation.' });
    return;
  }

  const videoSnap = await adminDb.collection('videos').doc(parsed.data.videoId).get();
  if (videoSnap.exists && videoSnap.data()?.ownerId !== uid) {
    res.status(403).json({ error: 'You do not own this video.' });
    return;
  }

  const result = await queueVideoTranscode({
    videoId: parsed.data.videoId,
    ownerId: uid,
    fileName: parsed.data.fileName,
    mimeType: parsed.data.mimeType,
    fileSizeBytes: parsed.data.fileSizeBytes
  });

  res.json(result);
});

videosRouter.get('/:videoId/status', requireAuthenticatedUser, async (req: AuthenticatedRequest, res) => {
  const rawVideoId = req.params.videoId;
  const videoId = (Array.isArray(rawVideoId) ? rawVideoId[0] : rawVideoId)?.trim();
  if (!videoId || !/^[a-zA-Z0-9_-]+$/.test(videoId)) {
    res.status(400).json({ error: 'Invalid videoId' });
    return;
  }

  const jobSnap = await adminDb.collection('videoProcessingJobs').doc(`job_${videoId}`).get();
  if (!jobSnap.exists) {
    res.status(404).json({ error: 'Processing job not found' });
    return;
  }

  const job = jobSnap.data()!;
  if (job.ownerId !== req.user!.uid && req.user!.owner !== true) {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }

  res.json({ jobId: jobSnap.id, ...job });
});
