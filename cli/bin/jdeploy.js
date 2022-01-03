#! /usr/bin/env node
var shell = require("shelljs/global");
var userArgs = process.argv.slice(2);
var javaArgs = [];
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
cmd += ' -jar "'+__dirname+'/JDeploy.jar" ';
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