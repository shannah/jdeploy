#! /usr/bin/env node
var jarName = "{{JAR_NAME}}";
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
cmd += ' -jar "'+__dirname+'/'+jarName+'" ';
programArgs.forEach(function(arg) {
    cmd += ' "'+arg+'"';
});
exec(cmd);