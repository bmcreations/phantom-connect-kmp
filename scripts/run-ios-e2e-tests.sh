#!/usr/bin/env bash
set -euo pipefail

# ─── iOS E2E test runner ───
# Spins up WireMock (Docker), builds the iOS sample app, installs on simulator,
# and runs Maestro E2E tests.
#
# Usage: ./scripts/run-ios-e2e-tests.sh [flow]
#   flow: optional path to a specific Maestro flow (default: maestro/full-flow.yaml)
#
# Prerequisites:
#   - Xcode + iOS simulator installed
#   - Docker (for WireMock)
#   - Maestro CLI installed
#   - XCFramework already built:
#     ./gradlew :phantom-connect:assemblePhantomConnectKMPDebugXCFramework

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

FLOW="${1:-maestro/full-flow.yaml}"
WIREMOCK_CONTAINER="phantom-mock-e2e"
SIMULATOR_NAME="iPhone 16 Pro"
APP_BUNDLE_ID="dev.bmcreations.phantom.connect.sample"

# Find booted simulator or boot one
get_simulator_udid() {
    # Check for already booted simulator
    local booted
    booted=$(xcrun simctl list devices booted -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    for d in devices:
        if d['state'] == 'Booted' and 'iPhone' in d['name']:
            print(d['udid'])
            sys.exit(0)
" 2>/dev/null || true)

    if [ -n "$booted" ]; then
        echo "$booted"
        return
    fi

    # Find and boot a simulator
    local udid
    udid=$(xcrun simctl list devices available -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    if 'iOS' not in runtime:
        continue
    for d in devices:
        if d['name'] == '$SIMULATOR_NAME' and d['isAvailable']:
            print(d['udid'])
            sys.exit(0)
" 2>/dev/null || true)

    if [ -z "$udid" ]; then
        echo "❌ No '$SIMULATOR_NAME' simulator found." >&2
        exit 1
    fi

    echo "Booting simulator $SIMULATOR_NAME ($udid)..." >&2
    xcrun simctl boot "$udid" 2>/dev/null || true
    echo "$udid"
}

cleanup() {
    echo ""
    echo "=== Cleaning up ==="
    if docker ps -q --filter "name=$WIREMOCK_CONTAINER" 2>/dev/null | grep -q .; then
        echo "Stopping WireMock container..."
        docker rm -f "$WIREMOCK_CONTAINER" 2>/dev/null || true
    fi
    echo "Cleanup complete."
}
trap cleanup EXIT

# ─── Step 1: Start WireMock ───
echo "=== Starting WireMock mock server ==="
docker rm -f "$WIREMOCK_CONTAINER" 2>/dev/null || true
docker run -d \
    --name "$WIREMOCK_CONTAINER" \
    -p 8080:8080 \
    -v "$PROJECT_DIR/maestro/mock-server/wiremock:/home/wiremock" \
    wiremock/wiremock:3.3.1 \
    --verbose

echo "Waiting for WireMock..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/__admin/mappings >/dev/null 2>&1; then
        echo "WireMock ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "❌ WireMock failed to start."
        exit 1
    fi
    sleep 1
done

# ─── Step 2: Get simulator ───
echo ""
echo "=== Preparing iOS simulator ==="
SIMULATOR_UDID=$(get_simulator_udid)
echo "Using simulator: $SIMULATOR_UDID"

# ─── Step 3: Build iOS app ───
echo ""
echo "=== Building iOS sample app ==="
xcodebuild \
    -project sample-ios/PhantomSample.xcodeproj \
    -scheme PhantomSample \
    -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
    -configuration Debug \
    build 2>&1 | tail -5

# Find the built app
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData/PhantomSample-*/Build/Products/Debug-iphonesimulator/PhantomSample.app -maxdepth 0 2>/dev/null | head -1)
if [ -z "$APP_PATH" ]; then
    echo "❌ Built app not found in DerivedData."
    exit 1
fi
echo "App built at: $APP_PATH"

# ─── Step 4: Install app ───
echo ""
echo "=== Installing app on simulator ==="
xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
echo "App installed."

# ─── Step 5: Run Maestro E2E tests ───
echo ""
echo "=== Running Maestro E2E tests ==="
echo "Flow: $FLOW"
echo "Device: $SIMULATOR_UDID"

set +e
maestro --device "$SIMULATOR_UDID" test "$FLOW"
TEST_EXIT=$?
set -e

if [ $TEST_EXIT -eq 0 ]; then
    echo ""
    echo "✅ iOS E2E tests passed!"
else
    echo ""
    echo "❌ iOS E2E tests failed (exit code $TEST_EXIT)."
    LATEST_TEST=$(ls -td "$HOME/.maestro/tests/"* 2>/dev/null | head -1)
    if [ -n "$LATEST_TEST" ]; then
        echo "Debug artifacts: $LATEST_TEST"
    fi
fi

exit $TEST_EXIT
