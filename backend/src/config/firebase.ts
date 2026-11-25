import { applicationDefault, getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

const projectId = process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0489321426';
const databaseId =
  process.env.FIRESTORE_DATABASE_ID ||
  'ai-studio-android-youtvsui-f8408d23-5872-4e6d-ba45-b4798ace1352';

const app =
  getApps().length > 0
    ? getApps()[0]
    : initializeApp({
        credential: applicationDefault(),
        projectId
      });

export const adminAuth = getAuth(app);
export const adminDb = getFirestore(app, databaseId);
export const configuredProjectId = projectId;
export const configuredDatabaseId = databaseId;
