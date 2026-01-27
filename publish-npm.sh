#!/bin/bash
# Publish Luna SDK React Native to npm
# Run with: ./publish-npm.sh

set -e

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║        Luna SDK React Native NPM Publishing Script           ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

cd luna-sdk-react-native

# Check npm login
echo "📋 Step 1: Checking npm authentication..."
npm whoami || {
    echo "❌ Not logged in to npm. Please run: npm login"
    exit 1
}

echo ""
echo "📋 Step 2: Installing dependencies..."
npm install

echo ""
echo "📋 Step 3: Running type check..."
npm run typescript

echo ""
echo "📋 Step 4: Building package..."
npm run prepare

echo ""
echo "📋 Step 5: Publishing to npm..."
npm publish --access public

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    ✅ NPM PUBLISH COMPLETE                   ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Package published: @selenus/luna-sdk                        ║"
echo "║  Install with: npm install @selenus/luna-sdk                 ║"
echo "╚══════════════════════════════════════════════════════════════╝"
