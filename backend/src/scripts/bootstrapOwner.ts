import 'dotenv/config';
import { FieldValue } from 'firebase-admin/firestore';
import { adminAuth, adminDb } from '../config/firebase.js';

async function bootstrapOwner(): Promise<void> {
  const ownerEmail = process.env.OWNER_EMAIL?.trim();
  if (!ownerEmail) {
    console.error('ERROR: OWNER_EMAIL environment variable is required.');
    process.exit(1);
  }

  const user = await adminAuth.getUserByEmail(ownerEmail);
  if (!user.emailVerified) {
    console.error(
      `ERROR: User ${ownerEmail} (${user.uid}) does not have a verified email. Sign in with verified Google Auth first.`
    );
    process.exit(1);
  }

  await adminAuth.setCustomUserClaims(user.uid, {
    owner: true,
    role: 'OWNER'
  });

  await adminDb.collection('admins').doc(user.uid).set(
    {
      uid: user.uid,
      email: ownerEmail,
      role: 'OWNER',
      createdAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  await adminDb.collection('platformSettings').doc('global').set(
    {
      registrationsEnabled: true,
      uploadsEnabled: true,
      maintenanceMode: false,
      maxUploadSizeMb: 512,
      dailyUploadLimit: 20,
      allowedFormats: ['mp4', 'webm', 'mov'],
      categories: ['Music', 'Education', 'Entertainment', 'Technology', 'Gaming', 'Sports'],
      supportedLanguages: ['en', 'bn', 'hi'],
      autoFlagKeywords: ['spam', 'scam'],
      notificationsEnabled: true,
      transcoderConfigured: process.env.TRANSCODER_ENABLED === 'true',
      totalStorageQuotaMb: 10240,
      updatedBy: user.uid,
      updatedAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  await adminDb.collection('auditLogs').add({
    actorUid: user.uid,
    action: 'BOOTSTRAP_OWNER',
    targetType: 'ADMIN',
    targetId: user.uid,
    result: 'SUCCESS',
    summary: `Granted OWNER custom claim and /admins/${user.uid} record to ${ownerEmail}`,
    createdAt: FieldValue.serverTimestamp()
  });

  console.log(`Owner bootstrapped successfully for ${ownerEmail} (UID: ${user.uid}).`);
}

bootstrapOwner().catch((err) => {
  console.error('Bootstrap failed:', err);
  process.exit(1);
});
