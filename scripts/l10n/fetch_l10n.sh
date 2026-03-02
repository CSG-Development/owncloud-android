#!/bin/bash

VENV_DIR=".venv"

pushd "${SRCROOT}"

if [ ! -d "$VENV_DIR" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$VENV_DIR" || exit 1
fi

if [ -f "$VENV_DIR/bin/activate" ]; then
    source "$VENV_DIR/bin/activate"
else
    echo "Error: Unable to activate the virtual environment."
fi

if ! [ -x "$(command -v localization_tool)" ]; then
	pip install git+https://gitlabfr.noveogroup.com/internal/localization-tool.git
    #pip install -e ../../localization-tool
fi

localization_tool -c scripts/L10n/config.yaml
deactivate
popd
