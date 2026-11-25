import { Router } from 'express';
import { FieldValue } from 'firebase-admin/firestore';
import { z } from 'zod';
import { adminAuth, adminDb } from '../config/firebase.js';
import {
  type AuthenticatedRequest,
  requireAuthenticatedUser,
  requireVerifiedOwner
} from '../middleware/auth.js';
import { deleteVideoStorageObjects } from '../services/transcoder.js';

export const adminRouter = Router();

adminRouter.use(requireAuthenticatedUser, requireVerifiedOwner);

adminRouter.get('/verify', (req: AuthenticatedRequest, res) => {
  res.json({
    owner: true,
    uid: req.user!.uid,
    email: req.user!.email ?? '',
    message: 'Verified platform owner via Cloud Run & Firebase Custom Claims.'
  });
});

const moderateUserSchema = z.object({
  actionType: z.enum([
    'SUSPEND_USER',
    'RESTORE_USER',
    'BLOCK_USER',
    'UNBLOCK_USER',
    'DISABLE_UPLOADS',
    'ENABLE_UPLOADS'
  ]),
  reason: z.string().trim().min(2).max(500)
});

adminRouter.post('/users/:uid/moderate', async (req: AuthenticatedRequest, res) => {
  const parsed = moderateUserSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid moderation payload' });
    return;
  }

  const rawUid = req.params.uid;
  const targetUid = Array.isArray(rawUid) ? rawUid[0] : rawUid;
  const { actionType, reason } = parsed.data;
  const ownerUid = req.user!.uid;

  const updates: Record<string, unknown> = {
    moderationReason: reason,
    moderatedBy: ownerUid,
    updatedAt: FieldValue.serverTimestamp()
  };

  if (actionType === 'SUSPEND_USER') {
    updates.status = 'suspended';
    updates.uploadDisabled = true;
    await adminAuth.revokeRefreshTokens(targetUid);
  } else if (actionType === 'BLOCK_USER') {
    updates.status = 'blocked';
    updates.uploadDisabled = true;
    await adminAuth.updateUser(targetUid, { disabled: true });
    await adminAuth.revokeRefreshTokens(targetUid);
  } else if (actionType === 'RESTORE_USER' || actionType === 'UNBLOCK_USER') {
    updates.status = 'active';
    updates.uploadDisabled = false;
    await adminAuth.updateUser(targetUid, { disabled: false });
  } else if (actionType === 'DISABLE_UPLOADS') {
    updates.uploadDisabled = true;
  } else if (actionType === 'ENABLE_UPLOADS') {
    updates.uploadDisabled = false;
  }

  const batch = adminDb.batch();
  batch.set(adminDb.collection('users').doc(targetUid), updates, { merge: true });
  batch.set(adminDb.collection('moderationActions').doc(), {
    adminUid: ownerUid,
    adminEmail: req.user!.email ?? '',
    actionType,
    targetType: 'USER',
    targetId: targetUid,
    reason,
    createdAt: FieldValue.serverTimestamp()
  });
  batch.set(adminDb.collection('auditLogs').doc(), {
    actorUid: ownerUid,
    action: actionType,
    targetType: 'USER',
    targetId: targetUid,
    result: 'SUCCESS',
    summary: `${actionType} on ${targetUid}: ${reason}`,
    createdAt: FieldValue.serverTimestamp()
  });
  await batch.commit();

  res.json({ ok: true, targetUid, actionType });
});

const moderateVideoSchema = z.object({
  actionType: z.enum(['BLOCK_VIDEO', 'RESTORE_VIDEO', 'DELETE_VIDEO', 'CHANGE_VISIBILITY']),
  visibility: z.enum(['PUBLIC', 'PRIVATE', 'UNLISTED']).optional(),
  reason: z.string().trim().min(2).max(500)
});

adminRouter.post('/videos/:videoId/moderate', async (req: AuthenticatedRequest, res) => {
  const parsed = moderateVideoSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid video moderation payload' });
    return;
  }

  const rawVideoId = req.params.videoId;
  const videoId = Array.isArray(rawVideoId) ? rawVideoId[0] : rawVideoId;
  const { actionType, visibility, reason } = parsed.data;
  const ownerUid = req.user!.uid;
  const vidRef = adminDb.collection('videos').doc(videoId);

  const batch = adminDb.batch();
  let deletedObjects = 0;

  if (actionType === 'DELETE_VIDEO') {
    batch.delete(vidRef);
    deletedObjects = await deleteVideoStorageObjects(videoId);
  } else if (actionType === 'BLOCK_VIDEO') {
    batch.update(vidRef, { moderationStatus: 'BLOCKED', updatedAt: FieldValue.serverTimestamp() });
  } else if (actionType === 'RESTORE_VIDEO') {
    batch.update(vidRef, { moderationStatus: 'ACTIVE', updatedAt: FieldValue.serverTimestamp() });
  } else if (actionType === 'CHANGE_VISIBILITY') {
    batch.update(vidRef, { visibility: visibility ?? 'PRIVATE', updatedAt: FieldValue.serverTimestamp() });
  }

  batch.set(adminDb.collection('moderationActions').doc(), {
    adminUid: ownerUid,
    adminEmail: req.user!.email ?? '',
    actionType,
    targetType: 'VIDEO',
    targetId: videoId,
    reason,
    createdAt: FieldValue.serverTimestamp()
  });
  batch.set(adminDb.collection('auditLogs').doc(), {
    actorUid: ownerUid,
    action: actionType,
    targetType: 'VIDEO',
    targetId: videoId,
    result: 'SUCCESS',
    summary: `${actionType} on video ${videoId} (storage objects removed: ${deletedObjects})`,
    createdAt: FieldValue.serverTimestamp()
  });
  await batch.commit();

  res.json({ ok: true, videoId, actionType, deletedObjects });
});
