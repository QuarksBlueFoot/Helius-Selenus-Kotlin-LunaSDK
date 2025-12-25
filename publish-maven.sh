#!/bin/bash
# Publish LunaSDK to Maven Central
# Run with: ./publish-maven.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           LunaSDK Maven Central Publishing Script            ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Check for required environment variables
if [ -z "$OSSRH_USERNAME" ] || [ -z "$OSSRH_PASSWORD" ]; then
    echo "❌ Error: OSSRH_USERNAME and OSSRH_PASSWORD environment variables required"
    echo "   Set them with:"
    echo "   export OSSRH_USERNAME=your-username"
    echo "   export OSSRH_PASSWORD=your-password"
    exit 1
fi

if [ -z "$SIGNING_KEY_ID" ] || [ -z "$SIGNING_PASSWORD" ]; then
    echo "❌ Error: SIGNING_KEY_ID and SIGNING_PASSWORD environment variables required"
    echo "   Set them with:"
    echo "   export SIGNING_KEY_ID=your-key-id"
    echo "   export SIGNING_PASSWORD=your-signing-password"
    exit 1
fi

echo "📋 Step 1: Clean build"
./gradlew clean

echo ""
echo "📋 Step 2: Run tests"
./gradlew :luna-sdk:test

echo ""
echo "📋 Step 3: Build release artifacts"
./gradlew :luna-sdk:build

echo ""
echo "📋 Step 4: Publish to Maven Central Staging"
./gradlew :luna-sdk:publish \
    -Psigning.keyId=$SIGNING_KEY_ID \
    -Psigning.password=$SIGNING_PASSWORD \
    -PossrhUsername=$OSSRH_USERNAME \
    -PossrhPassword=$OSSRH_PASSWORD

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    ✅ PUBLISH COMPLETE                       ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Next steps:                                                 ║"
echo "║  1. Go to https://s01.oss.sonatype.org                      ║"
echo "║  2. Login with your OSSRH credentials                       ║"
echo "║  3. Go to 'Staging Repositories'                            ║"
echo "║  4. Find your staged repository                             ║"
echo "║  5. Click 'Close' then 'Release'                            ║"
echo "╚══════════════════════════════════════════════════════════════╝"
