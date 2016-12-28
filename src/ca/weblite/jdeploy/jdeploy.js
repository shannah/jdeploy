#! /usr/bin/env node
var path = require('path');
var jarName = "{{JAR_NAME}}";
var mainClass = "{{MAIN_CLASS}}";
var classPath = "{{CLASSPATH}}";
var port = "{{PORT}}";
var warPath = "{{WAR_PATH}}";
classPath = classPath.split(':');
var classPathStr = '';
var first = true;
classPath.forEach(function(part) {
    if (!first) classPathStr += path.delimiter;
    first = false;
    classPathStr += __dirname + '/' + part;
});
classPath = classPathStr;
var shell = require("shelljs/global");
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

process.stdin.on('readable', () => {
  var chunk = process.stdin.read();
  if (chunk === null) {
      
      return;
  }
  try {
    child.stdin.write(chunk);
  } catch(e){}
});
child.on('close', function(code) {
    process.exit(code);
});