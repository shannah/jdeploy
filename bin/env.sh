#!/bin/bash
set -e
SCRIPTPATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Adding $SCRIPTPATH to PATH"
export PATH=$SCRIPTPATH:$PATH