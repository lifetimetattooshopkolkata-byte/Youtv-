import { Storage } from '@google-cloud/storage';
import { TranscoderServiceClient } from '@google-cloud/video-transcoder';
import { FieldValue } from 'firebase-admin/firestore';
import { adminDb, configuredProjectId } from '../config/firebase.js';

export interface TranscodeRequestInput {
  videoId: string;
  ownerId: string;
  fileName: string;
  mimeType: string;
  fileSizeBytes: number;
}

export interface TranscodeQueueResult {
  transcoderConfigured: boolean;
  jobId: string;
  signedUploadUrl?: string;
  hlsOutputPath?: string;
  message: string;
}

const storageBucket = process.env.GCS_BUCKET?.trim() || '';
const location = process.env.GCP_LOCATION?.trim() || 'us-central1';
const transcoderEnabled =
  process.env.TRANSCODER_ENABLED === 'true' && storageBucket.length > 0;

export function isTranscoderConfigured(): boolean {
  return transcoderEnabled;
}

export async function queueVideoTranscode(
  input: TranscodeRequestInput
): Promise<TranscodeQueueResult> {
  const safeName = input.fileName.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 100);
  const inputObjectPath = `uploads/original/${input.ownerId}/${input.videoId}/${safeName}`;
  const outputFolder = `processed/${input.videoId}/`;
  const manifestPath = `${outputFolder}manifest.m3u8`;
  const jobId = `job_${input.videoId}`;

  if (!transcoderEnabled) {
    await adminDb.collection('videoProcessingJobs').doc(jobId).set(
      {
        videoId: input.videoId,
        ownerId: input.ownerId,
        inputPath: inputObjectPath,
        outputManifestPath: null,
        status: 'FALLBACK_DIRECT',
        progressPercent: 100,
        errorMessage:
          'Google Cloud Transcoder API or GCS_BUCKET is not configured; using direct playback fallback.',
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );

    return {
      transcoderConfigured: false,
      jobId,
      message:
        'Transcoder API setup required (set GCS_BUCKET and TRANSCODER_ENABLED=true). Safe direct playback fallback active.'
    };
  }

  const storage = new Storage({ projectId: configuredProjectId });
  const bucket = storage.bucket(storageBucket);
  const file = bucket.file(inputObjectPath);
  const [signedUploadUrl] = await file.getSignedUrl({
    version: 'v4',
    action: 'write',
    expires: Date.now() + 30 * 60 * 1000,
    contentType: input.mimeType
  });

  const transcoder = new TranscoderServiceClient();
  const parent = transcoder.locationPath(configuredProjectId, location);

  await adminDb.collection('videoProcessingJobs').doc(jobId).set(
    {
      videoId: input.videoId,
      ownerId: input.ownerId,
      inputPath: `gs://${storageBucket}/${inputObjectPath}`,
      outputManifestPath: `https://storage.googleapis.com/${storageBucket}/${manifestPath}`,
      status: 'QUEUED',
      progressPercent: 10,
      errorMessage: null,
      updatedAt: FieldValue.serverTimestamp(),
      createdAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  try {
    const [job] = await transcoder.createJob({
      parent,
      job: {
        inputUri: `gs://${storageBucket}/${inputObjectPath}`,
        outputUri: `gs://${storageBucket}/${outputFolder}`,
        config: {
          elementaryStreams: [
            {
              key: 'video-1080p',
              videoStream: { h264: { heightPixels: 1080, widthPixels: 1920, bitrateBps: 5000000, frameRate: 30 } }
            },
            {
              key: 'video-720p',
              videoStream: { h264: { heightPixels: 720, widthPixels: 1280, bitrateBps: 2500000, frameRate: 30 } }
            },
            {
              key: 'video-480p',
              videoStream: { h264: { heightPixels: 480, widthPixels: 854, bitrateBps: 1200000, frameRate: 30 } }
            },
            {
              key: 'video-360p',
              videoStream: { h264: { heightPixels: 360, widthPixels: 640, bitrateBps: 600000, frameRate: 30 } }
            },
            {
              key: 'audio-aac',
              audioStream: { codec: 'aac', bitrateBps: 128000 }
            }
          ],
          muxStreams: [
            { key: '1080p', container: 'ts', elementaryStreams: ['video-1080p', 'audio-aac'] },
            { key: '720p', container: 'ts', elementaryStreams: ['video-720p', 'audio-aac'] },
            { key: '480p', container: 'ts', elementaryStreams: ['video-480p', 'audio-aac'] },
            { key: '360p', container: 'ts', elementaryStreams: ['video-360p', 'audio-aac'] }
          ],
          manifests: [
            {
              fileName: 'manifest.m3u8',
              type: 'HLS',
              muxStreams: ['1080p', '720p', '480p', '360p']
            }
          ]
        }
      }
    });

    await adminDb.collection('videoProcessingJobs').doc(jobId).set(
      {
        status: 'TRANSCODING',
        transcoderJobName: job.name ?? null,
        progressPercent: 35,
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );
  } catch (err) {
    await adminDb.collection('videoProcessingJobs').doc(jobId).set(
      {
        status: 'QUEUED',
        progressPercent: 15,
        errorMessage: err instanceof Error ? err.message : 'Waiting for source upload before transcode starts',
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );
  }

  return {
    transcoderConfigured: true,
    jobId,
    signedUploadUrl,
    hlsOutputPath: `https://storage.googleapis.com/${storageBucket}/${manifestPath}`,
    message: 'Signed GCS upload URL generated and Transcoder job registered.'
  };
}

export async function deleteVideoStorageObjects(videoId: string): Promise<number> {
  if (!storageBucket) return 0;
  const storage = new Storage({ projectId: configuredProjectId });
  const bucket = storage.bucket(storageBucket);
  const [files] = await bucket.getFiles({ prefix: `processed/${videoId}/` });
  await Promise.all(files.map((f) => f.delete({ ignoreNotFound: true })));
  return files.length;
}
