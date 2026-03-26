#!/bin/sh

git config core.hooksPath .github/.hooks
chmod +x .github/.hooks/pre-commit
git add .github/.hooks/pre-commit .github/.hooks/setup_hooks.sh

