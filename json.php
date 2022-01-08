#!/usr/bin/env php
<?php
$data = json_decode(file_get_contents("package.json"), true);
$address = explode('.', $argv[1]);
foreach ($address as $key) {
    if (is_array($data) and isset($data[$key])) {
        $data = $data[$key];
    }
}
if (is_scalar($data)) echo $data;
else echo json_encode($data);