#! /usr/bin/env node

var jarName = "{{JAR_NAME}}";
var mainClass = "{{MAIN_CLASS}}";
var classPath = "{{CLASSPATH}}";
var port = "{{PORT}}";
var warPath = "{{WAR_PATH}}";
var javaVersionString = "{{JAVA_VERSION}}";


function njreWrap() {
    'use strict'

    const path = require('path')
    const fs = require('fs')
    const os = require('os')
    const crypto = require('crypto')
    const fetch = require('node-fetch')
    const yauzl = require('yauzl')
    const tar = require('tar')

    function createDir (dir) {
      return new Promise((resolve, reject) => {
        fs.access(dir, err => {
          if (err && err.code === 'ENOENT') {
            fs.mkdir(dir, err => {
              if (err) reject(err)
              resolve()
            })
          } else if (!err) resolve()
          else reject(err)
        })
      })
    }

    function download (dir, url) {
      return new Promise((resolve, reject) => {
        createDir(dir)
          .then(() => fetch(url))
          .then(response => {
            const destFile = path.join(dir, path.basename(url))
            const destStream = fs.createWriteStream(destFile)
            response.body.pipe(destStream).on('finish', () => resolve(destFile))
          })
          .catch(err => reject(err))
      })
    }

    function downloadAll (dir, url) {
      return download(dir, url + '.sha256.txt').then(() => download(dir, url))
    }

    function genChecksum (file) {
      return new Promise((resolve, reject) => {
        fs.readFile(file, (err, data) => {
          if (err) reject(err)

          resolve(
            crypto
              .createHash('sha256')
              .update(data)
              .digest('hex')
          )
        })
      })
    }

    function verify (file) {
      return new Promise((resolve, reject) => {
        fs.readFile(file + '.sha256.txt', 'utf-8', (err, data) => {
          if (err) reject(err)

          genChecksum(file).then(checksum => {
            checksum === data.split('  ')[0]
              ? resolve(file)
              : reject(new Error('File and checksum don\'t match'))
          })
        })
      })
    }

    function move (file) {
      return new Promise((resolve, reject) => {
        const jdeployDir = path.join(os.homedir(), '.jdeploy');
        if (!fs.existsSync(jdeployDir)) {
            fs.mkdirSync(jdeployDir);
        }

        var jreDir = path.join(jdeployDir, 'jre');
        if (!fs.existsSync(jreDir)) {
            fs.mkdirSync(jreDir);
        }

        jreDir = path.join(jreDir, javaVersionString);
        if (!fs.existsSync(jreDir)) {
            fs.mkdirSync(jreDir);
        }
        const newFile = path.join(jreDir, file.split(path.sep).slice(-1)[0])

        fs.copyFile(file, newFile, err => {
          if (err) reject(err)

          fs.unlink(file, err => {
            if (err) reject(err)
            resolve(newFile)
          })
        })
      })
    }

    function extractZip (file, dir) {
      return new Promise((resolve, reject) => {
        yauzl.open(file, { lazyEntries: true }, (err, zipFile) => {
          if (err) reject(err)

          zipFile.readEntry()
          zipFile.on('entry', entry => {
            const entryPath = path.join(dir, entry.fileName)

            if (/\/$/.test(entry.fileName)) {
              fs.mkdir(entryPath, { recursive: true }, err => {
                if (err && err.code !== 'EEXIST') reject(err)

                zipFile.readEntry()
              })
            } else {
              zipFile.openReadStream(entry, (err, readStream) => {
                if (err) reject(err)

                readStream.on('end', () => {
                  zipFile.readEntry()
                })
                readStream.pipe(fs.createWriteStream(entryPath))
              })
            }
          })
          zipFile.once('close', () => {
            fs.unlink(file, err => {
              if (err) reject(err)
              resolve(dir)
            })
          })
        })
      })
    }

    function extractTarGz (file, dir) {
      return tar.x({ file: file, cwd: dir }).then(() => {
        return new Promise((resolve, reject) => {
          fs.unlink(file, err => {
            if (err) reject(err)
            resolve(dir)
          })
        })
      })
    }

    function extract (file) {
      const dir = path.join(path.dirname(file), 'jre')

      return createDir(dir).then(() => {
        return path.extname(file) === '.zip'
          ? extractZip(file, dir)
          : extractTarGz(file, dir)
      })
    }

    /**
     * Installs a JRE copy for the app
     * @param {number} [version = 8] - Java Version (`8`/`9`/`10`/`11`/`12`)
     * @param {object} [options] - Installation Options
     * @param {string} [options.os] - Operating System (defaults to current) (`windows`/`mac`/`linux`/`solaris`/`aix`)
     * @param {string} [options.arch] - Architecture (defaults to current) (`x64`/`x32`/`ppc64`/`s390x`/`ppc64le`/`aarch64`/`sparcv9`)
     * @param {string} [options.openjdk_impl = hotspot] - OpenJDK Implementation (`hotspot`/`openj9`)
     * @param {string} [options.release = latest] - Release
     * @param {string} [options.type = jre] - Binary Type (`jre`/`jdk`)
     * @param {string} [options.heap_size] - Heap Size (`normal`/`large`)
     * @return Promise<string> - Resolves to the installation directory or rejects an error
     * @example
     * const njre = require('njre')
     *
     * // Use default options
     * njre.install()
     *   .then(dir => {
     *     // Do stuff
     *   })
     *   .catch(err => {
     *     // Handle the error
     *   })
     *
     * // or custom ones
     * njre.install(11, { os: 'aix', arch: 'ppc64', openjdk_impl: 'openj9' })
     *   .then(dir => {
     *     // Do stuff
     *   })
     *   .catch(err => {
     *     // Handle the error
     *   })
     */
    function install (version = 8, options = {}) {
      const { openjdk_impl = 'hotspot', release = 'latest', type = 'jre' } = options
      options = { ...options, openjdk_impl, release, type }
      let url = 'https://api.adoptopenjdk.net/v2/info/releases/openjdk' + version + '?'

      if (!options.os) {
        switch (process.platform) {
          case 'aix':
            options.os = 'aix'
            break
          case 'darwin':
            options.os = 'mac'
            break
          case 'linux':
            options.os = 'linux'
            break
          case 'sunos':
            options.os = 'solaris'
            break
          case 'win32':
            options.os = 'windows'
            break
          default:
            return Promise.reject(new Error('Unsupported operating system'))
        }
      }
      if (!options.arch) {
        if (/^ppc64|s390x|x32|x64$/g.test(process.arch)) options.arch = process.arch
        else if (process.arch === 'ia32') options.arch = 'x32'
        else if (process.arch === 'arm64') options.arch = 'aarch64'
        else return Promise.reject(new Error('Unsupported architecture'))
      }

      Object.keys(options).forEach(key => { url += key + '=' + options[key] + '&' })

      const tmpdir = path.join(os.tmpdir(), 'njre')

      return fetch(url)
        .then(response => response.json())
        .then(json => downloadAll(tmpdir, json.binaries[0]['binary_link']))
        .then(verify)
        .then(move)
        .then(extract)
    }

    return {install:install};



}


var fs = require('fs');
var os = require('os');
var path = require('path');
const njre = njreWrap();
const targetJavaVersion = parseInt(javaVersionString);
var shell = require("shelljs/global");
function getJdeploySupportDir() {
    return os.homedir() + path.sep + ".jdeploy";
}

function getJavaVersion(binPath) {

    var oldPath = env['PATH'];
    if (binPath) {
        env['PATH'] = binPath + path.delimiter + env['PATH'];
    }

    try {
        var javaVersionProc = exec('java  -version', {silent:true});
        if (javaVersionProc.code !== 0) {
            return false;
        }
        var stdout = javaVersionProc.stderr;
        //console.log(javaVersionProc);
        //console.log("stdout is "+stdout);
        var regexp = /version "(.*?)"/;
        var match = regexp.exec(stdout);
        var parts = match[1].split('.');
        var join = '.';
        var versionStr = '';
        parts.forEach(function(v) {
            versionStr += v;
            if (join !== null) {
                versionStr += join;
                join = null;
            }
        });
        versionStr = versionStr.replace('_', '');
        //console.log("Java version string "+versionStr)
        return parseFloat(versionStr);
    } catch (e) {
        return false;
    } finally {
        env['PATH'] = oldPath;
    }
}
var getDirectories = dirPath => fs.readdirSync(dirPath).filter(
    file => fs.statSync(path.join(dirPath, file)).isDirectory()
  );

function getEmbeddedJavaHome() {
    var _platform = os.platform();
    var _driver = '';
    switch (_platform) {
      case 'darwin': _platform = 'macosx'; _driver = 'Contents' + path.sep + 'Home'; break;
      case 'win32': _platform = 'windows'; _driver = ''; break;
      case 'linux': _driver = ''; break;
      default:
        fail('unsupported platform: ' + _platform);
    }

    var jreDir = path.join(os.homedir(), '.jdeploy',  'jre', javaVersionString, 'jre');

    try {
        return jreDir + path.sep + getDirectories(jreDir)[0] + (_driver ? (path.sep + _driver) : '');
    } catch (e) {
        //console.log(e);
        return jreDir;
    }
}

function javaVersionMatch(v1, v2) {
    if (v1 === 8) v1 = 1.8;
    if (v2 === 8) v2 = 1.8;
    if (Math.floor(v1) !== Math.floor(v2)) {

        return false;
    }
    if (v1 < 2) {
        // Up to 1.8, the version would be like 1.7, 1.8, etc..
        // So we need to check the minor version for equivalency
        return (Math.floor(v1*10) === Math.floor(v2*10));
    } else {
        // Starting with Java 9, the version is like 9, 10, 11, etc..
        // so we just compare major version.
        return (Math.floor(v1) === Math.floor(v2));
    }

}

var done = false;
if (env['JAVA_HOME']) {
    var javaHomeVersion = getJavaVersion(path.join(env['JAVA_HOME'], 'bin'));
    if (javaVersionMatch(javaHomeVersion, targetJavaVersion)) {
        done = true;
        env['PATH'] = path.join(env['JAVA_HOME'], 'bin') + path.delimiter + env['PATH'];
        run(env['JAVA_HOME']);

    }
}

if (!done) {
    var javaVersion = getJavaVersion();
    if (javaVersionMatch(javaVersion, targetJavaVersion)) {
        done = true;
        run();
    }
}

if (!done) {

    var _javaHome = getEmbeddedJavaHome();
    var javaVersion = getJavaVersion(path.join(_javaHome, 'bin'));
    if (javaVersionMatch(javaVersion, targetJavaVersion)) {

        env['PATH'] = path.join(_javaHome, 'bin') + path.delimiter + env['PATH'];
        env['JAVA_HOME'] = _javaHome;
        done = true;
        run(_javaHome);
    }
}

if (!done) {
    console.log("Downloading java runtime environment for version "+targetJavaVersion);
    njre.install(targetJavaVersion).then(function(dir) {
        let dirCont = fs.readdirSync( dir );
        if (os.platform() == 'darwin') {
            env['JAVA_HOME'] = path.join(dir, dirCont[0], 'Contents', 'Home');
        } else {
            env['JAVA_HOME'] = path.join(dir, dirCont[0]);
        }
        env['PATH'] = path.join(env['JAVA_HOME'], 'bin') + path.delimiter + env['PATH'];

        run(env['JAVA_HOME']);
    }).catch(function(err) {
        console.log("Failed to install JRE", err);
    });
}




function run(_javaHome) {
    var fail = reason => {
        console.error(reason);
        process.exit(1);
    };


    classPath = classPath.split(':');
    var classPathStr = '';
    var first = true;
    classPath.forEach(function(part) {
        if (!first) classPathStr += path.delimiter;
        first = false;
        classPathStr += __dirname + '/' + part;
    });
    classPath = classPathStr;

    var userArgs = process.argv.slice(2);
    var javaArgs = [];
    javaArgs.push('-Djdeploy.base='+__dirname);
    javaArgs.push('-Djdeploy.port='+port);
    javaArgs.push('-Djdeploy.war.path='+warPath);
    var programArgs = [];
    userArgs.forEach(function(arg) {
        if (arg.startsWith('-D') || arg.startsWith('-X')) {
            javaArgs.push(arg);
        } else {
            programArgs.push(arg);
        }
    });
    var cmd = 'java';

    if (!_javaHome) {
        env['PATH'] = path.join(getEmbeddedJavaHome(), 'bin') + path.delimiter + env['PATH'];
        if (env['JAVA_HOME']) {
            env['PATH'] = env['JAVA_HOME'] + path.sep + 'bin' + path.delimiter + env['PATH'];
        }

    }


    var javaVersion = getJavaVersion();

    //console.log("Java version is "+getJavaVersion());

    javaArgs.forEach(function(arg) {
        cmd += ' "'+arg+'"';
    });
    if (jarName !== '{'+'{JAR_NAME}}') {
        cmd += ' -jar "'+__dirname+'/'+jarName+'" ';
    } else {
        cmd += ' -cp "'+classPath+'" '+mainClass+' ';
    }

    programArgs.forEach(function(arg) {
        cmd += ' "'+arg+'"';
    });
    var child = exec(cmd, {async: true});
    process.stdin.setEncoding('utf8');

    process.stdin.on('readable', function() {
      var chunk = null;
      while (null !== (chunk = process.stdin.read())) {
        try {
          child.stdin.write(chunk);
        } catch(e){}
      }
    });
    child.on('close', function(code) {
        process.exit(code);
    });

}
