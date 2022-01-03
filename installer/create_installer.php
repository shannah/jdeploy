<?php
$name = $argv[1];
if (!$name) exit(1);

$zip = new ZipArchive;
$res = $zip->open('jdeploy/installers/mac/jdeploy-installer-mac.zip');
if ($res === TRUE) {
    $len = $zip->count();
    echo "Found $len entries\n";
    for ($i=0; $i<$len; $i++) {
        $entryName = $zip->getNameIndex($i);

        if (preg_match('#^jdeploy-installer/jdeploy-installer\.app/(.*)$#', $entryName, $matches)) {
            $newName = $name.'-installer/'.$name.'-installer.app/'.$matches[1];
            echo "Changing $entryName to $newName\n";
            $zip->renameIndex($i, $newName);
        }
    }
    for ($i=0; $i<$len; $i++) {
        $entryName = $zip->getNameIndex($i);

        if (preg_match('#^jdeploy-installer/(.*)$#', $entryName, $matches)) {
            $newName = $name.'-installer/'.$matches[1];
            echo "Changing $entryName to $newName\n";
            $zip->renameIndex($i, $newName);
        }
    }
    $zip->close();
} else {
    echo 'failed, code:' . $res;
}