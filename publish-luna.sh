#!/bin/bash

# Credentials for Central Portal
# NOTE: These must be generated from https://central.sonatype.com/account -> Generate Token
# export CENTRAL_USERNAME="<your_token_username>"
# export CENTRAL_PASSWORD="<your_token_password>"

# GPG Keys
# export SIGNING_KEY="$(cat secret.asc)"
# export SIGNING_PASSWORD="<your_gpg_passphrase>"

if [ -z "$CENTRAL_USERNAME" ]; then
  echo "Error: CENTRAL_USERNAME is not set."
  exit 1
fi

if [ -z "$CENTRAL_PASSWORD" ]; then
  echo "Error: CENTRAL_PASSWORD is not set."
  exit 1
fi

# GPG Agent mode: SIGNING_KEY not needed, key is imported into GPG
if [ "$USE_GPG_AGENT" != "true" ]; then
  if [ -z "$SIGNING_KEY" ]; then
    echo "Error: SIGNING_KEY is not set (required unless USE_GPG_AGENT=true)."
    exit 1
  fi
fi

if [ -z "$SIGNING_PASSWORD" ]; then
  echo "Error: SIGNING_PASSWORD is not set."
  exit 1
fi

# Configure GPG for non-interactive use
if [ "$USE_GPG_AGENT" = "true" ]; then
  echo "GPG agent mode enabled - passphrase should be preset by CI"
  
  # List the keys to verify
  echo "Listing secret keys:"
  gpg --list-secret-keys --keyid-format LONG
  
  # Get the key ID from the imported key (avoids BOM issues with env vars)
  KEYID=$(gpg --list-secret-keys --keyid-format LONG 2>/dev/null | grep -oP "rsa\d+/\K[A-F0-9]{16}" | head -1)
  echo "Detected key ID: $KEYID"
  
  # Test gpg signing works (using the preset passphrase from ghaction-import-gpg)
  echo "Testing GPG signing..."
  echo "test" | gpg --batch --yes --armor --detach-sign > /dev/null 2>&1 && echo "GPG signing test passed!" || echo "GPG signing test failed (passphrase may need to be preset)"
  
  # Set up gradle.properties with signing configuration
  echo "Configuring Gradle for GPG signing..."
  echo "" >> gradle.properties
  echo "signing.gnupg.executable=gpg" >> gradle.properties
  echo "signing.gnupg.useLegacyGpg=false" >> gradle.properties
  echo "signing.gnupg.keyName=$KEYID" >> gradle.properties
fi

# Get version from gradle.properties
VERSION=$(grep "VERSION_NAME" gradle.properties | cut -d'=' -f2)
echo "Detected version: $VERSION"

# Remove any local Java home setting from gradle.properties (CI provides its own Java)
sed -i '/org.gradle.java.home/d' gradle.properties

echo "1. Cleaning and Building Staging Repository..."
rm -rf luna-sdk/build
./gradlew :luna-sdk:clean :luna-sdk:publishMavenPublicationToStagingRepository -Pversion=$VERSION --no-daemon || exit 1

echo "2. Zipping Bundle..."
if [ ! -d "luna-sdk/build/staging-deploy" ]; then
    echo "Error: Staging directory not found at luna-sdk/build/staging-deploy"
    exit 1
fi

cd luna-sdk/build/staging-deploy
zip -r ../luna-sdk.zip .
cd ../../..

echo "3. Uploading to Central Portal..."
# Construct Base64 Auth Header
AUTH_STRING=$(echo -n "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" | base64 | tr -d '\n')

curl --request POST \
  --url 'https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC' \
  --header "Authorization: Bearer ${AUTH_STRING}" \
  --form bundle=@luna-sdk/build/luna-sdk.zip

echo "Done."
