import assert from 'node:assert/strict';
import test from 'node:test';
import { isOwnerClaimAndEmailMatch, normalizeEmail } from '../middleware/auth.js';
import {
  DistributionManager,
  assertSafeReleaseObjectPath,
  isValidHttpsHostingUrl,
  sanitizeApkFileName,
  verifyTimingSafeSecret
} from '../services/distributionManager.js';

test('normalizeEmail trims and lowercases cleanly', () => {
  assert.equal(normalizeEmail('  Owner@YouTV.Example '), 'owner@youtv.example');
  assert.equal(normalizeEmail(undefined), '');
});

test('isOwnerClaimAndEmailMatch enforces verified email, matching OWNER_EMAIL, and custom claims', () => {
  const ownerEmail = 'owner@youtv.example';

  assert.equal(
    isOwnerClaimAndEmailMatch(
      { email: 'owner@youtv.example', email_verified: true, owner: true, role: 'OWNER' },
      ownerEmail
    ),
    true
  );

  assert.equal(
    isOwnerClaimAndEmailMatch(
      { email: 'owner@youtv.example', email_verified: false, owner: true, role: 'OWNER' },
      ownerEmail
    ),
    false
  );

  assert.equal(
    isOwnerClaimAndEmailMatch(
      { email: 'attacker@youtv.example', email_verified: true, owner: true, role: 'OWNER' },
      ownerEmail
    ),
    false
  );

  assert.equal(
    isOwnerClaimAndEmailMatch(
      { email: 'owner@youtv.example', email_verified: true, owner: false, role: 'USER' },
      ownerEmail
    ),
    false
  );

  assert.equal(
    isOwnerClaimAndEmailMatch(
      { email: 'owner@youtv.example', email_verified: true, owner: true, role: 'OWNER' },
      undefined
    ),
    false
  );
});

test('DistributionManager path confinement blocks traversal and allows releases/apk/ paths', () => {
  assert.equal(
    assertSafeReleaseObjectPath('releases/apk/rel_1/YouTV-ADMIT-v1.0-release.apk'),
    'releases/apk/rel_1/YouTV-ADMIT-v1.0-release.apk'
  );
  assert.throws(() => assertSafeReleaseObjectPath('releases/apk/../../secrets.txt'));
  assert.throws(() => assertSafeReleaseObjectPath('uploads/original/user/app.apk'));
});

test('DistributionManager validates HTTPS hosting URLs, sanitizes APK names, and verifies webhook secrets', () => {
  assert.equal(sanitizeApkFileName('1.2.0-beta+1', 'release'), 'YouTV-ADMIT-v1.2.0-beta_1-release.apk');
  assert.equal(isValidHttpsHostingUrl('https://youtv-app.web.app'), true);
  assert.equal(isValidHttpsHostingUrl('http://youtv-app.web.app'), false);
  assert.equal(isValidHttpsHostingUrl('javascript:alert(1)'), false);

  assert.equal(verifyTimingSafeSecret('super-secret-token', 'super-secret-token'), true);
  assert.equal(verifyTimingSafeSecret('wrong-token', 'super-secret-token'), false);
  assert.equal(verifyTimingSafeSecret('', 'super-secret-token'), false);

  const mgr = new DistributionManager({
    gcsBucket: '',
    firebaseHostingUrl: 'https://gen-lang-client-0489321426.web.app',
    cloudBuildTriggerId: '',
    webhookSecret: 'webhook-key'
  });
  const summary = mgr.getStatusSummary();
  assert.equal(summary.gcsConfigured, false);
  assert.equal(summary.firebaseHostingConfigured, true);
  assert.equal(summary.cloudBuildConfigured, false);
  assert.equal(mgr.verifyWebhookSecret('webhook-key'), true);
  assert.equal(mgr.verifyWebhookSecret('bad-key'), false);
});
