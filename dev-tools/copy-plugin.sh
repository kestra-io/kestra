#!/bin/bash
###############################################################################
# Script : copy-plugin.sh
# Description : Script to automate the build and deployment of Kestra plugins locally
#
# This script performs the following operations:
# 1. Checks and configures the KESTRA_PLUGINS_DIR environment variable
# 2. Executes a Gradle build with shadowJar task
# 3. Copies all generated JAR files to the Kestra plugins directory
#
# Usage:
#   ./copy-plugin.sh
#
# Prerequisites:
#   - bash or zsh shell
#   - Gradle installed or using the Gradle wrapper (./gradlew)
#   - Write access to ~/.zshrc
#   - Write permissions on Kestra plugins directory
#
# Environment variables:
#   KESTRA_PLUGINS_DIR : Path to Kestra plugins directory
#                        If not defined, script will prompt for it
#
# Return codes:
#   0 : Success
#   1 : Error (invalid directory, build failure, copy error)
#
# Notes:
#   - Script will automatically add KESTRA_PLUGINS_DIR to ~/.zshrc
#   - JAR files are searched in all build/libs subdirectories
###############################################################################

# Check if KESTRA_PLUGINS_DIR is set
if [ -z "$KESTRA_PLUGINS_DIR" ]; then
  # Prompt the user for the Kestra plugins directory
  echo -n "KESTRA_PLUGINS_DIR is not set. Please enter the path to your Kestra plugins directory: "
  read plugins_dir

  # Validate if the entered directory is not empty
  if [ -z "$plugins_dir" ]; then
    echo "Error: Plugin directory cannot be empty. Exiting."
    exit 1
  fi

  # Expand tilde (~) if present in the input path
  plugins_dir=$(eval echo "$plugins_dir")

  # Add export to ~/.zshrc
  echo "Adding 'export KESTRA_PLUGINS_DIR=\"$plugins_dir\"' to ~/.zshrc"
  echo "export KESTRA_PLUGINS_DIR=\"$plugins_dir\"" >> ~/.zshrc

  # Source .zshrc to make the variable available in the current session
  source ~/.zshrc
  echo "KESTRA_PLUGINS_DIR has been set and sourced."
else
  echo "KESTRA_PLUGINS_DIR is already set to: $KESTRA_PLUGINS_DIR"
fi

# Ensure the KESTRA_PLUGINS_DIR exists
if [ ! -d "$KESTRA_PLUGINS_DIR" ]; then
  echo "Error: KESTRA_PLUGINS_DIR ($KESTRA_PLUGINS_DIR) does not exist or is not a directory. Please create it or set the correct path."
  exit 1
fi

echo "Starting Gradle build..."
# Run ./gradlew shadowJar
if ./gradlew shadowJar; then
  echo "Gradle build (shadowJar) completed successfully."

  # Find all 'build/libs' directories and copy *.jar files from them
  found_jars=0
  find . -type d -name "libs" -path "*/build/libs" | while read -r build_libs_dir; do
    echo "Found build/libs directory: $build_libs_dir"
    if ls "$build_libs_dir"/*.jar &>/dev/null; then # Check if there are any .jar files
      echo "Copying *.jar files from $build_libs_dir to $KESTRA_PLUGINS_DIR"
      cp "$build_libs_dir"/*.jar "$KESTRA_PLUGINS_DIR"
      if [ $? -eq 0 ]; then
        echo "Successfully copied JAR files from $build_libs_dir"
        found_jars=1 # Set flag if at least one copy operation was successful
      else
        echo "Error: Failed to copy JAR files from $build_libs_dir."
      fi
    else
      echo "No *.jar files found in $build_libs_dir."
    fi
  done

  # Check if any JARs were found and copied
  if [ "$found_jars" -eq 0 ]; then
    echo "Warning: No *.jar files were found in any 'build/libs' directories or failed to copy."
    # Exit 1 for a warning, or 0 if it's acceptable that no JARs are found
  fi

else
  echo "Error: Gradle build (shadowJar) failed. Please check the build output for errors."
  exit 1
fi

echo "Script finished."
