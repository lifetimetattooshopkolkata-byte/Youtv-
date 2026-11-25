import type { NextFunction, Request, Response } from 'express';
import type { DecodedIdToken } from 'firebase-admin/auth';
import { adminAuth, adminDb } from '../config/firebase.js';

export interface AuthenticatedRequest extends Request {
  user?: DecodedIdToken;
  isOwner?: boolean;
}

export function normalizeEmail(email: string | undefined | null): string {
  return (email ?? '').trim().toLowerCase();
}

export function isOwnerClaimAndEmailMatch(
  decoded: Pick<DecodedIdToken, 'email' | 'email_verified'> & Record<string, unknown>,
  configuredOwnerEmail: string | undefined
): boolean {
  const expected = normalizeEmail(configuredOwnerEmail);
  if (!expected) return false;
  if (decoded.email_verified !== true) return false;
  if (normalizeEmail(decoded.email) !== expected) return false;
  return decoded.owner === true && decoded.role === 'OWNER';
}

export async function requireAuthenticatedUser(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> {
  const header = req.header('authorization') ?? '';
  if (!header.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Missing Bearer Authorization header' });
    return;
  }

  const token = header.slice('Bearer '.length).trim();
  if (!token) {
    res.status(401).json({ error: 'Empty ID token' });
    return;
  }

  try {
    const decoded = await adminAuth.verifyIdToken(token, true);
    if (!decoded.email_verified) {
      res.status(403).json({ error: 'Verified email required' });
      return;
    }
    req.user = decoded;
    next();
  } catch {
    res.status(401).json({ error: 'Invalid or expired Firebase ID token' });
  }
}

export async function requireVerifiedOwner(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): Promise<void> {
  if (!req.user) {
    res.status(401).json({ error: 'Authentication required' });
    return;
  }

  const configuredOwner = process.env.OWNER_EMAIL;
  if (!configuredOwner || !configuredOwner.trim()) {
    res.status(503).json({
      owner: false,
      error: 'OWNER_EMAIL is not configured on backend server.'
    });
    return;
  }

  if (!isOwnerClaimAndEmailMatch(req.user, configuredOwner)) {
    res.status(403).json({
      owner: false,
      error: 'Access denied: account is not the verified platform owner.'
    });
    return;
  }

  try {
    const adminDoc = await adminDb.collection('admins').doc(req.user.uid).get();
    if (!adminDoc.exists || adminDoc.data()?.role !== 'OWNER') {
      res.status(403).json({
        owner: false,
        error: 'Access denied: /admins owner record missing.'
      });
      return;
    }
    req.isOwner = true;
    next();
  } catch {
    res.status(500).json({ error: 'Failed to verify owner authorization state' });
  }
}
