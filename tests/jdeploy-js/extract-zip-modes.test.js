#!/usr/bin/env node
/*
 * Regression test for https://github.com/shannah/jdeploy/issues/465
 *
 * The generated launcher (cli/.../jdeploy.js) downloads and unpacks the JRE at
 * runtime. On macOS/Windows the JRE ships as a .zip, extracted by the
 * launcher's `extractZip` function. That function used to write every entry
 * with `fs.createWriteStream` and no mode, dropping the Unix execute bit on
 * every JRE binary except bin/java (which was chmod'd as a special case). The
 * result: lib/jspawnhelper was left non-executable and any launched app that
 * spawned a child process died with "posix_spawn failed, error: 0".
 *
 * This test exercises the REAL `extractZip` implementation lifted straight out
 * of jdeploy.js. It builds a zip that stores Unix modes (an executable file, a
 * jspawnhelper stand-in, and a plain non-executable file), extracts it, and
 * asserts the execute bits are preserved. If the mode-preservation is removed,
 * the "executable" assertions fail.
 *
 * The test is a no-op on Windows (no Unix execute bit) and skips gracefully if
 * the `zip` CLI is unavailable.
 */

'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

let yauzl;
try {
  yauzl = require('yauzl');
} catch (e) {
  console.error('yauzl is not installed. Run `npm install` in tests/jdeploy-js first.');
  process.exit(2);
}

const JDEPLOY_JS = path.resolve(
  __dirname,
  '..',
  '..',
  'cli',
  'src',
  'main',
  'resources',
  'ca',
  'weblite',
  'jdeploy',
  'jdeploy.js'
);

// Pull the real `extractZip` function body out of jdeploy.js by brace matching,
// so this test tracks the shipped implementation rather than a copy of it.
function extractFunctionSource(source, name) {
  const marker = 'function ' + name;
  const start = source.indexOf(marker);
  if (start < 0) {
    throw new Error('Could not find `' + marker + '` in ' + JDEPLOY_JS);
  }
  let depth = 0;
  let started = false;
  for (let i = source.indexOf('{', start); i < source.length; i++) {
    const ch = source[i];
    if (ch === '{') {
      depth++;
      started = true;
    } else if (ch === '}') {
      depth--;
      if (started && depth === 0) {
        return source.slice(start, i + 1);
      }
    }
  }
  throw new Error('Unbalanced braces while extracting `' + name + '`');
}

function loadExtractZip() {
  const source = fs.readFileSync(JDEPLOY_JS, 'utf8');
  const body = extractFunctionSource(source, 'extractZip');
  // extractZip only depends on yauzl, path and fs from its surrounding scope.
  return new Function('yauzl', 'path', 'fs', body + '\nreturn extractZip;')(yauzl, path, fs);
}

function haveZipCli() {
  try {
    execFileSync('zip', ['--version'], { stdio: 'ignore' });
    return true;
  } catch (e) {
    return false;
  }
}

function isExecutable(p) {
  return (fs.statSync(p).mode & 0o111) !== 0;
}

async function main() {
  if (process.platform === 'win32') {
    console.log('SKIP: extract-zip-modes test does not apply on Windows (no Unix execute bit).');
    return;
  }
  if (!haveZipCli()) {
    console.log('SKIP: `zip` CLI not available; cannot build a mode-preserving fixture.');
    return;
  }

  const extractZip = loadExtractZip();

  const workRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'jdeploy-extractzip-'));
  try {
    // Build a source tree with known Unix modes.
    const srcDir = path.join(workRoot, 'src');
    fs.mkdirSync(path.join(srcDir, 'bin'), { recursive: true });
    fs.mkdirSync(path.join(srcDir, 'lib'), { recursive: true });
    fs.writeFileSync(path.join(srcDir, 'bin', 'java'), '#!/bin/sh\necho java\n');
    fs.writeFileSync(path.join(srcDir, 'lib', 'jspawnhelper'), 'spawnhelper\n');
    fs.writeFileSync(path.join(srcDir, 'release'), 'JAVA_VERSION="21"\n');
    fs.chmodSync(path.join(srcDir, 'bin', 'java'), 0o755);
    fs.chmodSync(path.join(srcDir, 'lib', 'jspawnhelper'), 0o755);
    fs.chmodSync(path.join(srcDir, 'release'), 0o644);

    // Zip it up, preserving Unix modes in the high 16 bits of externalFileAttributes.
    const zipPath = path.join(workRoot, 'jre.zip');
    execFileSync('zip', ['-r', '-q', zipPath, '.'], { cwd: srcDir });

    // extractZip deletes the archive it extracts, so hand it a throwaway copy.
    const workZip = path.join(workRoot, 'work.zip');
    fs.copyFileSync(zipPath, workZip);

    const outDir = path.join(workRoot, 'out');
    fs.mkdirSync(outDir, { recursive: true });

    await extractZip(workZip, outDir);

    const checks = [
      ['bin/java preserved execute bit', isExecutable(path.join(outDir, 'bin', 'java'))],
      ['lib/jspawnhelper preserved execute bit', isExecutable(path.join(outDir, 'lib', 'jspawnhelper'))],
      ['release stayed non-executable', !isExecutable(path.join(outDir, 'release'))],
    ];

    let failed = false;
    for (const [name, ok] of checks) {
      console.log((ok ? 'PASS' : 'FAIL') + ': ' + name);
      if (!ok) failed = true;
    }

    if (failed) {
      console.error(
        '\nextractZip did not preserve Unix file modes. Executables such as ' +
          'lib/jspawnhelper would be left non-executable, breaking child-process ' +
          'spawning on macOS/Windows (posix_spawn failed, error: 0). See issue #465.'
      );
      process.exit(1);
    }

    console.log('\nAll extract-zip-modes checks passed.');
  } finally {
    fs.rmSync(workRoot, { recursive: true, force: true });
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(2);
});
