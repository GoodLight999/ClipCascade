'use strict';

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const mode = process.argv[2] === 'production' ? 'production' : 'full';
const omitDev = mode === 'production';
const logDir = path.resolve('ci-logs', 'android');
const rawLog = path.join(logDir, `npm-audit-${mode}.json`);
const acceptedUrls = new Set([
  'https://github.com/advisories/GHSA-w3rx-r6r6-pgpr',
  'https://github.com/advisories/GHSA-5p2g-fcmc-qvqq',
]);
const waiverExpires = '2026-08-31';

fs.mkdirSync(logDir, { recursive: true });

const npmArgs = ['audit', '--json'];
if (omitDev) npmArgs.push('--omit=dev');
const audit = spawnSync('npm', npmArgs, {
  encoding: 'utf8',
  maxBuffer: 32 * 1024 * 1024,
});

if (!audit.stdout) {
  process.stderr.write(audit.stderr || 'npm audit produced no JSON output\n');
  process.exit(1);
}

fs.writeFileSync(rawLog, audit.stdout);

let report;
try {
  report = JSON.parse(audit.stdout);
} catch (error) {
  process.stderr.write(`Unable to parse npm audit JSON: ${error.message}\n`);
  process.exit(1);
}

const vulnerabilities = report.vulnerabilities || {};
const highOrCritical = Object.keys(vulnerabilities).filter(name => {
  const severity = vulnerabilities[name]?.severity;
  return severity === 'high' || severity === 'critical';
});

if (highOrCritical.length === 0) {
  console.log(`${mode}: no high or critical npm advisories`);
  process.exit(0);
}

const today = new Date().toISOString().slice(0, 10);
if (today > waiverExpires) {
  console.error(
    `Temporary image-size advisory waiver expired on ${waiverExpires}; re-evaluate upstream before extending it.`,
  );
  process.exit(1);
}

let imageSizeVersion;
let metroVersion;
let metroImageSizeRange;
try {
  imageSizeVersion = require('../node_modules/image-size/package.json').version;
  const metroPackage = require('../node_modules/metro/package.json');
  metroVersion = metroPackage.version;
  metroImageSizeRange = metroPackage.dependencies?.['image-size'];
} catch (error) {
  console.error(`Unable to verify Metro/image-size dependency shape: ${error.message}`);
  process.exit(1);
}

if (
  imageSizeVersion !== '1.2.1' ||
  metroVersion !== '0.82.5' ||
  metroImageSizeRange !== '^1.0.2'
) {
  console.error(
    `Temporary waiver dependency shape changed: metro=${metroVersion} range=${metroImageSizeRange} image-size=${imageSizeVersion}`,
  );
  process.exit(1);
}

const evaluating = new Set();
const memo = new Map();

function advisoryAllowed(via) {
  return (
    via &&
    typeof via === 'object' &&
    via.name === 'image-size' &&
    via.severity === 'high' &&
    acceptedUrls.has(via.url)
  );
}

function vulnerabilityAllowed(name) {
  if (memo.has(name)) return memo.get(name);
  if (evaluating.has(name)) return false;

  const vulnerability = vulnerabilities[name];
  if (!vulnerability) return false;

  evaluating.add(name);
  const via = Array.isArray(vulnerability.via) ? vulnerability.via : [];
  const allowed =
    via.length > 0 &&
    via.every(item => {
      if (typeof item === 'string') return vulnerabilityAllowed(item);
      return advisoryAllowed(item);
    });
  evaluating.delete(name);
  memo.set(name, allowed);
  return allowed;
}

const rejected = highOrCritical.filter(name => !vulnerabilityAllowed(name));
if (rejected.length > 0) {
  console.error(
    `${mode}: unaccepted high/critical npm vulnerabilities: ${rejected.join(', ')}`,
  );
  process.exit(1);
}

const directImageSizeAdvisories = (vulnerabilities['image-size']?.via || [])
  .filter(item => typeof item === 'object')
  .map(item => item.url)
  .filter(Boolean);
const unexpectedImageSizeAdvisories = directImageSizeAdvisories.filter(
  url => !acceptedUrls.has(url),
);
if (unexpectedImageSizeAdvisories.length > 0) {
  console.error(
    `image-size has advisories outside the exact temporary waiver: ${unexpectedImageSizeAdvisories.join(', ')}`,
  );
  process.exit(1);
}

console.log(
  `${mode}: accepted only the two unpatched image-size DoS advisories propagated through Metro 0.82.5`,
);
console.log(`image-size=${imageSizeVersion}; waiver expires=${waiverExpires}`);
console.log(`raw audit JSON: ${rawLog}`);
