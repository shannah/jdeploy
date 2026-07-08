#!/usr/bin/env node
/*
 * Regression test for package.json "jdeploy.args" support in the generated
 * launcher (cli/.../jdeploy.js).
 *
 * The launcher must honour JVM/program args declared in package.json's
 * jdeploy.args array, including the platform-conditional prefix syntax
 * ("-[mac]...", "-D[win]...", "-X[linux]...") and the "--flag value" splitting
 * that the JVM requires for options like --add-opens. This mirrors the
 * processArg()/processRunArgs() behaviour of the client4j launcher.
 *
 * The test exercises the REAL processPackageArg/appendPackageArgs functions
 * lifted straight out of jdeploy.js (by brace matching) so it tracks the
 * shipped implementation rather than a copy of it. process.platform is
 * temporarily overridden to exercise each platform branch on any host.
 */

'use strict';

const fs = require('fs');
const path = require('path');
const assert = require('assert');

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

function loadArgFns() {
  const source = fs.readFileSync(JDEPLOY_JS, 'utf8');
  const body =
    extractFunctionSource(source, 'processPackageArg') +
    '\n' +
    extractFunctionSource(source, 'appendPackageArgs') +
    '\nreturn { processPackageArg: processPackageArg, appendPackageArgs: appendPackageArgs };';
  // Both functions only depend on the global `process` and `Array`.
  return new Function(body)();
}

// Runs `fn` with process.platform pinned to `platform`, then restores it.
function withPlatform(platform, fn) {
  const original = Object.getOwnPropertyDescriptor(process, 'platform');
  Object.defineProperty(process, 'platform', { value: platform, configurable: true });
  try {
    return fn();
  } finally {
    Object.defineProperty(process, 'platform', original);
  }
}

function categorize(fns, rawArgs, platform) {
  return withPlatform(platform, function () {
    const javaArgs = [];
    const programArgs = [];
    fns.appendPackageArgs(rawArgs, javaArgs, programArgs);
    return { javaArgs: javaArgs, programArgs: programArgs };
  });
}

function main() {
  const fns = loadArgFns();

  // --- The motivating case: mac-only --add-opens is split into two JVM args ---
  const macAddOpens = ['-[mac]--add-opens java.desktop/com.apple.eawt=ALL-UNNAMED'];

  assert.deepStrictEqual(
    categorize(fns, macAddOpens, 'darwin'),
    {
      javaArgs: ['--add-opens', 'java.desktop/com.apple.eawt=ALL-UNNAMED'],
      programArgs: [],
    },
    'mac --add-opens should be split into flag + value on macOS'
  );

  assert.deepStrictEqual(
    categorize(fns, macAddOpens, 'win32'),
    { javaArgs: [], programArgs: [] },
    'mac-only arg should be dropped on Windows'
  );
  assert.deepStrictEqual(
    categorize(fns, macAddOpens, 'linux'),
    { javaArgs: [], programArgs: [] },
    'mac-only arg should be dropped on Linux'
  );

  // --- Unconditional --add-opens is always split, on every platform ---
  const bareAddOpens = ['--add-opens java.base/java.lang=ALL-UNNAMED'];
  ['darwin', 'win32', 'linux'].forEach(function (p) {
    assert.deepStrictEqual(
      categorize(fns, bareAddOpens, p),
      {
        javaArgs: ['--add-opens', 'java.base/java.lang=ALL-UNNAMED'],
        programArgs: [],
      },
      'unconditional --add-opens should be split on ' + p
    );
  });

  // --- -D[win] / -X[linux] prefixes rewrite to -D.../-X... when they apply ---
  assert.deepStrictEqual(
    categorize(fns, ['-D[win]foo=bar'], 'win32'),
    { javaArgs: ['-Dfoo=bar'], programArgs: [] },
    '-D[win] should become -Dfoo=bar on Windows'
  );
  assert.deepStrictEqual(
    categorize(fns, ['-D[win]foo=bar'], 'linux'),
    { javaArgs: [], programArgs: [] },
    '-D[win] should be dropped off Windows'
  );
  assert.deepStrictEqual(
    categorize(fns, ['-X[linux]mx512m'], 'linux'),
    { javaArgs: ['-Xmx512m'], programArgs: [] },
    '-X[linux]mx512m should become -Xmx512m on Linux'
  );

  // --- Pipe-OR conditions: mac|linux applies on both, not Windows ---
  const orArg = ['-[mac|linux]--add-exports java.base/sun.nio.ch=ALL-UNNAMED'];
  assert.deepStrictEqual(
    categorize(fns, orArg, 'darwin').javaArgs,
    ['--add-exports', 'java.base/sun.nio.ch=ALL-UNNAMED'],
    'mac|linux applies on macOS'
  );
  assert.deepStrictEqual(
    categorize(fns, orArg, 'linux').javaArgs,
    ['--add-exports', 'java.base/sun.nio.ch=ALL-UNNAMED'],
    'mac|linux applies on Linux'
  );
  assert.deepStrictEqual(
    categorize(fns, orArg, 'win32'),
    { javaArgs: [], programArgs: [] },
    'mac|linux is dropped on Windows'
  );

  // --- Plain -D / -X pass through untouched; program args go after the jar ---
  assert.deepStrictEqual(
    categorize(fns, ['-Dfoo=bar', '-Xmx1g', 'myProgramArg'], 'linux'),
    { javaArgs: ['-Dfoo=bar', '-Xmx1g'], programArgs: ['myProgramArg'] },
    'plain -D/-X are JVM args; non-dash tokens are program args'
  );

  // --- Value-less double-dash flags are kept as a single JVM token ---
  assert.deepStrictEqual(
    categorize(fns, ['--enable-preview'], 'linux'),
    { javaArgs: ['--enable-preview'], programArgs: [] },
    'value-less --enable-preview stays a single token'
  );

  // --- Short module-path form -p is split into -p + value ---
  assert.deepStrictEqual(
    categorize(fns, ['-p /some/modules'], 'linux'),
    { javaArgs: ['-p', '/some/modules'], programArgs: [] },
    '-p <path> is split into two JVM tokens'
  );

  // --- Non-array / empty inputs are tolerated ---
  assert.deepStrictEqual(
    categorize(fns, [], 'linux'),
    { javaArgs: [], programArgs: [] },
    'empty args produce no output'
  );
  (function () {
    const javaArgs = [];
    const programArgs = [];
    fns.appendPackageArgs(undefined, javaArgs, programArgs);
    assert.deepStrictEqual({ javaArgs, programArgs }, { javaArgs: [], programArgs: [] },
      'undefined args are tolerated');
  })();

  console.log('package-args test passed');
}

main();
