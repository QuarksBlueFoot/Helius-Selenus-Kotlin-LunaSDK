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

if [ -z "$SIGNING_KEY" ]; then
  echo "Error: SIGNING_KEY is not set."
  exit 1
fi

if [ -z "$SIGNING_PASSWORD" ]; then
  echo "Error: SIGNING_PASSWORD is not set."
  exit 1
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
