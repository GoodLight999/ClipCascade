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

const imageSize = vulnerabilities['image-size'];
if (!imageSize || imageSize.severity !== 'high') {
  console.error('Expected waived image-size high vulnerability is missing or changed severity.');
  process.exit(1);
}

function isAcceptedDirectAdvisory(item) {
  return (
    item &&
    typeof item === 'object' &&
    item.name === 'image-size' &&
    item.dependency === 'image-size' &&
    item.severity === 'high' &&
    acceptedUrls.has(item.url)
  );
}

const imageSizeVia = Array.isArray(imageSize.via) ? imageSize.via : [];
const directImageSizeAdvisories = imageSizeVia.filter(
  item => typeof item === 'object',
);
const directImageSizeUrls = new Set(
  directImageSizeAdvisories.map(item => item.url).filter(Boolean),
);

if (
  imageSizeVia.some(item => typeof item === 'string') ||
  directImageSizeAdvisories.length !== acceptedUrls.size ||
  directImageSizeAdvisories.some(item => !isAcceptedDirectAdvisory(item)) ||
  directImageSizeUrls.size !== acceptedUrls.size ||
  [...acceptedUrls].some(url => !directImageSizeUrls.has(url))
) {
  console.error(
    'image-size advisory set changed; refusing to broaden the temporary waiver.',
  );
  process.exit(1);
}

// npm audit represents propagated vulnerabilities as an effects graph. Metro's
// graph contains cycles (metro <-> metro-config / metro-transform-worker), so a
// recursive "via" walk incorrectly treats an in-progress cycle as an unknown
// vulnerability. Build the exact transitive effects closure from image-size
// instead, then separately reject any direct advisory object that is not one of
// the two explicitly accepted GHSA records.
const propagatedClosure = new Set(['image-size']);
const queue = ['image-size'];
while (queue.length > 0) {
  const current = queue.shift();
  const effects = vulnerabilities[current]?.effects;
  if (!Array.isArray(effects)) continue;

  for (const affected of effects) {
    if (!vulnerabilities[affected] || propagatedClosure.has(affected)) continue;
    propagatedClosure.add(affected);
    queue.push(affected);
  }
}

const rejectedOutsideClosure = highOrCritical.filter(
  name => !propagatedClosure.has(name),
);
if (rejectedOutsideClosure.length > 0) {
  console.error(
    `${mode}: high/critical vulnerabilities outside the exact image-size propagation closure: ${rejectedOutsideClosure.join(', ')}`,
  );
  process.exit(1);
}

for (const name of highOrCritical) {
  const vulnerability = vulnerabilities[name];
  const via = Array.isArray(vulnerability.via) ? vulnerability.via : [];
  if (via.length === 0) {
    console.error(`${mode}: ${name} has no auditable via chain.`);
    process.exit(1);
  }

  for (const item of via) {
    if (typeof item === 'string') {
      if (!propagatedClosure.has(item)) {
        console.error(
          `${mode}: ${name} propagates through non-waived vulnerability ${item}.`,
        );
        process.exit(1);
      }
      continue;
    }

    if (!isAcceptedDirectAdvisory(item)) {
      console.error(
        `${mode}: ${name} contains a direct advisory outside the exact image-size waiver: ${item?.url || item?.name || 'unknown'}.`,
      );
      process.exit(1);
    }
  }
}

console.log(
  `${mode}: accepted only the two unpatched image-size DoS advisories and their npm-audit effects closure.`,
);
console.log(
  `accepted high/critical nodes: ${highOrCritical.slice().sort().join(', ')}`,
);
console.log(
  `metro=${metroVersion}; image-size=${imageSizeVersion}; waiver expires=${waiverExpires}`,
);
console.log(`raw audit JSON: ${rawLog}`);
