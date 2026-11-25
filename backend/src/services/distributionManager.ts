import { createHash, timingSafeEqual } from 'node:crypto';
import { Storage } from '@google-cloud/storage';
import { FieldValue } from 'firebase-admin/firestore';
import { adminDb, configuredProjectId } from '../config/firebase.js';

export type ApkBuildType = 'release' | 'debug';
export type ReleaseChannel = 'production' | 'beta';

export interface ApkBuildTriggerInput {
  versionName: string;
  versionCode: number;
  buildType: ApkBuildType;
  channel: ReleaseChannel;
  releaseNotes?: string;
}

export interface DistributionConfig {
  gcsBucket: string;
  firebaseHostingUrl: string;
  cloudBuildTriggerId: string;
  gcpLocation: string;
  webhookSecret: string;
}

export interface ApkReleaseRecord {
  releaseId: string;
  versionName: string;
  versionCode: number;
  buildType: ApkBuildType;
  channel: ReleaseChannel;
  releaseNotes: string;
  fileName: string;
  gcsObjectPath: string | null;
  hostingPath: string | null;
  cloudBuildId: string | null;
  status: 'QUEUED_CLOUD_BUILD' | 'SETUP_REQUIRED' | 'READY' | 'FAILED';
  sha256: string | null;
  fileSizeBytes: number;
  downloadCount: number;
  triggeredBy: string;
}

export interface DirectDownloadResult {
  releaseId: string;
  fileName: string;
  versionName: string;
  versionCode: number;
  sha256: string | null;
  source: 'GCS_SIGNED_URL' | 'FIREBASE_HOSTING' | 'UNCONFIGURED';
  downloadUrl: string | null;
  expiresInSeconds: number | null;
  message: string;
}

export function sanitizeApkFileName(versionName: string, buildType: ApkBuildType): string {
  const safeVersion = versionName.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 40) || '1.0';
  return `YouTV-ADMIT-v${safeVersion}-${buildType}.apk`;
}

export function assertSafeReleaseObjectPath(objectPath: string): string {
  const trimmed = objectPath.trim();
  if (!trimmed.startsWith('releases/apk/') || trimmed.includes('..') || trimmed.includes('\\')) {
    throw new Error('Invalid release storage path: must stay confined under releases/apk/.');
  }
  return trimmed;
}

export function isValidHttpsHostingUrl(rawUrl: string): boolean {
  if (!rawUrl.trim()) return false;
  try {
    const parsed = new URL(rawUrl.trim());
    return parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

export function verifyTimingSafeSecret(provided: string | undefined, expected: string): boolean {
  if (!expected || !provided) return false;
  const providedHash = createHash('sha256').update(provided).digest();
  const expectedHash = createHash('sha256').update(expected).digest();
  return timingSafeEqual(providedHash, expectedHash);
}

export class DistributionManager {
  private readonly config: DistributionConfig;

  constructor(config?: Partial<DistributionConfig>) {
    this.config = {
      gcsBucket: (config?.gcsBucket ?? process.env.GCS_BUCKET ?? '').trim(),
      firebaseHostingUrl: (config?.firebaseHostingUrl ?? process.env.FIREBASE_HOSTING_URL ?? '').trim().replace(/\/+$/, ''),
      cloudBuildTriggerId: (config?.cloudBuildTriggerId ?? process.env.CLOUD_BUILD_TRIGGER_ID ?? '').trim(),
      gcpLocation: (config?.gcpLocation ?? process.env.GCP_LOCATION ?? 'us-central1').trim(),
      webhookSecret: (config?.webhookSecret ?? process.env.DISTRIBUTION_WEBHOOK_SECRET ?? '').trim()
    };
  }

  getStatusSummary() {
    return {
      gcsConfigured: this.config.gcsBucket.length > 0,
      firebaseHostingConfigured: isValidHttpsHostingUrl(this.config.firebaseHostingUrl),
      cloudBuildConfigured: this.config.cloudBuildTriggerId.length > 0,
      firebaseHostingUrl: isValidHttpsHostingUrl(this.config.firebaseHostingUrl)
        ? this.config.firebaseHostingUrl
        : null
    };
  }

  verifyWebhookSecret(providedSecret: string | undefined): boolean {
    return verifyTimingSafeSecret(providedSecret, this.config.webhookSecret);
  }

  async triggerApkBuild(
    input: ApkBuildTriggerInput,
    ownerUid: string
  ): Promise<{ release: ApkReleaseRecord; setupRequired: boolean; message: string }> {
    const releaseId = `rel_${Date.now()}_${input.versionCode}`;
    const fileName = sanitizeApkFileName(input.versionName, input.buildType);
    const objectPath = assertSafeReleaseObjectPath(`releases/apk/${releaseId}/${fileName}`);
    const hostingPath = isValidHttpsHostingUrl(this.config.firebaseHostingUrl)
      ? `/releases/${fileName}`
      : null;

    let cloudBuildId: string | null = null;
    let status: ApkReleaseRecord['status'] = 'SETUP_REQUIRED';
    let setupRequired = true;
    let message =
      'Build record registered. Set CLOUD_BUILD_TRIGGER_ID and GCS_BUCKET to trigger automated Cloud Build APK signing and upload.';

    if (this.config.cloudBuildTriggerId && this.config.gcsBucket) {
      try {
        const storage = new Storage({ projectId: configuredProjectId });
        const accessToken = await storage.authClient.getAccessToken();
        const triggerEndpoint =
          `https://cloudbuild.googleapis.com/v1/projects/${encodeURIComponent(configuredProjectId)}` +
          `/locations/${encodeURIComponent(this.config.gcpLocation)}` +
          `/triggers/${encodeURIComponent(this.config.cloudBuildTriggerId)}:run`;

        const response = await fetch(triggerEndpoint, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            substitutions: {
              _RELEASE_ID: releaseId,
              _VERSION_NAME: input.versionName,
              _VERSION_CODE: String(input.versionCode),
              _BUILD_TYPE: input.buildType,
              _GCS_BUCKET: this.config.gcsBucket,
              _APK_OBJECT_PATH: objectPath
            }
          })
        });

        if (response.ok) {
          const payload = (await response.json()) as { metadata?: { build?: { id?: string } } };
          cloudBuildId = payload.metadata?.build?.id ?? `cb_${releaseId}`;
          status = 'QUEUED_CLOUD_BUILD';
          setupRequired = false;
          message = `Cloud Build APK job queued (${cloudBuildId}).`;
        } else {
          const errText = await response.text();
          status = 'FAILED';
          message = `Cloud Build trigger returned HTTP ${response.status}: ${errText.slice(0, 180)}`;
        }
      } catch (err) {
        status = 'FAILED';
        message = err instanceof Error ? err.message : 'Failed to reach Cloud Build API.';
      }
    }

    const record: ApkReleaseRecord = {
      releaseId,
      versionName: input.versionName.trim().slice(0, 40),
      versionCode: input.versionCode,
      buildType: input.buildType,
      channel: input.channel,
      releaseNotes: (input.releaseNotes ?? '').trim().slice(0, 1000),
      fileName,
      gcsObjectPath: this.config.gcsBucket ? objectPath : null,
      hostingPath,
      cloudBuildId,
      status,
      sha256: null,
      fileSizeBytes: 0,
      downloadCount: 0,
      triggeredBy: ownerUid
    };

    const batch = adminDb.batch();
    batch.set(adminDb.collection('appReleases').doc(releaseId), {
      ...record,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });
    batch.set(adminDb.collection('auditLogs').doc(), {
      actorUid: ownerUid,
      action: 'TRIGGER_APK_BUILD',
      targetType: 'APP_RELEASE',
      targetId: releaseId,
      result: status === 'FAILED' ? 'FAILURE' : 'SUCCESS',
      summary: `APK build (${input.buildType} v${input.versionName}) -> ${status}`,
      createdAt: FieldValue.serverTimestamp()
    });
    await batch.commit();

    return { release: record, setupRequired, message };
  }

  async generateSignedApkUploadUrl(
    releaseId: string,
    ownerUid: string
  ): Promise<{ signedUploadUrl: string; gcsObjectPath: string; expiresInSeconds: number }> {
    if (!this.config.gcsBucket) {
      throw new Error('GCS_BUCKET must be configured before generating a signed APK upload URL.');
    }

    const snap = await adminDb.collection('appReleases').doc(releaseId).get();
    if (!snap.exists) {
      throw new Error(`Release ${releaseId} not found.`);
    }
    const data = snap.data() as ApkReleaseRecord;
    const objectPath = assertSafeReleaseObjectPath(
      data.gcsObjectPath ?? `releases/apk/${releaseId}/${data.fileName}`
    );

    const expiresInSeconds = 15 * 60;
    const storage = new Storage({ projectId: configuredProjectId });
    const [signedUploadUrl] = await storage
      .bucket(this.config.gcsBucket)
      .file(objectPath)
      .getSignedUrl({
        version: 'v4',
        action: 'write',
        expires: Date.now() + expiresInSeconds * 1000,
        contentType: 'application/vnd.android.package-archive'
      });

    await adminDb.collection('auditLogs').add({
      actorUid: ownerUid,
      action: 'ISSUE_APK_UPLOAD_URL',
      targetType: 'APP_RELEASE',
      targetId: releaseId,
      result: 'SUCCESS',
      summary: `Issued v4 signed upload URL for ${objectPath}`,
      createdAt: FieldValue.serverTimestamp()
    });

    return { signedUploadUrl, gcsObjectPath: objectPath, expiresInSeconds };
  }

  async markReleaseReady(params: {
    releaseId: string;
    sha256: string;
    fileSizeBytes: number;
    gcsObjectPath?: string;
    hostingPath?: string;
  }): Promise<void> {
    const updates: Record<string, unknown> = {
      status: 'READY',
      sha256: params.sha256.trim().toLowerCase(),
      fileSizeBytes: params.fileSizeBytes,
      updatedAt: FieldValue.serverTimestamp()
    };
    if (params.gcsObjectPath) {
      updates.gcsObjectPath = assertSafeReleaseObjectPath(params.gcsObjectPath);
    }
    if (params.hostingPath) {
      const cleanPath = params.hostingPath.trim();
      if (!cleanPath.startsWith('/releases/')) {
        throw new Error('Hosting path must start with /releases/.');
      }
      updates.hostingPath = cleanPath;
    }

    await adminDb.collection('appReleases').doc(params.releaseId).set(updates, { merge: true });
  }

  async listReleases(limit = 20): Promise<ApkReleaseRecord[]> {
    const snap = await adminDb
      .collection('appReleases')
      .orderBy('createdAt', 'desc')
      .limit(Math.min(Math.max(limit, 1), 50))
      .get();

    return snap.docs.map((doc) => doc.data() as ApkReleaseRecord);
  }

  async resolveDirectApkDownload(releaseId: string): Promise<DirectDownloadResult> {
    const docRef = adminDb.collection('appReleases').doc(releaseId);
    const snap = await docRef.get();
    if (!snap.exists) {
      throw new Error('Release not found.');
    }

    const release = snap.data() as ApkReleaseRecord;
    if (release.status !== 'READY') {
      return {
        releaseId: release.releaseId,
        fileName: release.fileName,
        versionName: release.versionName,
        versionCode: release.versionCode,
        sha256: release.sha256,
        source: 'UNCONFIGURED',
        downloadUrl: null,
        expiresInSeconds: null,
        message: `Release ${release.versionName} is currently ${release.status} and not yet signed/published.`
      };
    }

    if (this.config.gcsBucket && release.gcsObjectPath) {
      const safePath = assertSafeReleaseObjectPath(release.gcsObjectPath);
      const expiresInSeconds = 15 * 60;
      const storage = new Storage({ projectId: configuredProjectId });
      const [signedUrl] = await storage
        .bucket(this.config.gcsBucket)
        .file(safePath)
        .getSignedUrl({
          version: 'v4',
          action: 'read',
          expires: Date.now() + expiresInSeconds * 1000,
          responseDisposition: `attachment; filename="${sanitizeApkFileName(release.versionName, release.buildType)}"`,
          responseType: 'application/vnd.android.package-archive'
        });

      await docRef.update({
        downloadCount: FieldValue.increment(1),
        updatedAt: FieldValue.serverTimestamp()
      });

      return {
        releaseId: release.releaseId,
        fileName: release.fileName,
        versionName: release.versionName,
        versionCode: release.versionCode,
        sha256: release.sha256,
        source: 'GCS_SIGNED_URL',
        downloadUrl: signedUrl,
        expiresInSeconds,
        message: 'Generated short-lived GCS signed APK download URL.'
      };
    }

    if (isValidHttpsHostingUrl(this.config.firebaseHostingUrl) && release.hostingPath) {
      const cleanPath = release.hostingPath.startsWith('/releases/')
        ? release.hostingPath
        : `/releases/${sanitizeApkFileName(release.versionName, release.buildType)}`;
      const hostingDownloadUrl = `${this.config.firebaseHostingUrl}${cleanPath}`;

      await docRef.update({
        downloadCount: FieldValue.increment(1),
        updatedAt: FieldValue.serverTimestamp()
      });

      return {
        releaseId: release.releaseId,
        fileName: release.fileName,
        versionName: release.versionName,
        versionCode: release.versionCode,
        sha256: release.sha256,
        source: 'FIREBASE_HOSTING',
        downloadUrl: hostingDownloadUrl,
        expiresInSeconds: null,
        message: 'Resolved direct Firebase Hosting signed APK download URL.'
      };
    }

    return {
      releaseId: release.releaseId,
      fileName: release.fileName,
      versionName: release.versionName,
      versionCode: release.versionCode,
      sha256: release.sha256,
      source: 'UNCONFIGURED',
      downloadUrl: null,
      expiresInSeconds: null,
      message: 'Configure GCS_BUCKET or FIREBASE_HOSTING_URL to serve signed APK downloads.'
    };
  }
}
