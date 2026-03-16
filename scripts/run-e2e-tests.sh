#!/usr/bin/env bash
set -euo pipefail

# ─── Self-contained E2E test runner ───
# Spins up WireMock (Docker), creates a headless emulator, builds the mock
# flavor of the sample app, runs Maestro E2E tests, and tears everything down.
#
# Usage: ./scripts/run-e2e-tests.sh [flow]
#   flow: optional path to a specific Maestro flow (default: maestro/full-flow.yaml)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

FLOW="${1:-maestro/full-flow.yaml}"
AVD_NAME="phantom-e2e-test"
ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVDMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager"
EMULATOR="$ANDROID_SDK/emulator/emulator"
ADB="$ANDROID_SDK/platform-tools/adb"
SYSTEM_IMAGE="system-images;android-35;google_apis;arm64-v8a"
WIREMOCK_CONTAINER="phantom-mock-e2e"

# Use homebrew adb if platform-tools not in SDK
if [ ! -f "$ADB" ]; then
    ADB="$(which adb 2>/dev/null || true)"
    if [ -z "$ADB" ]; then
        echo "❌ adb not found. Install Android platform-tools."
        exit 1
    fi
fi

cleanup() {
    echo ""
    echo "=== Cleaning up ==="

    # Kill emulator
    if [ -n "${EMULATOR_PID:-}" ]; then
        echo "Stopping emulator (PID $EMULATOR_PID)..."
        kill "$EMULATOR_PID" 2>/dev/null || true
        wait "$EMULATOR_PID" 2>/dev/null || true
    fi

    # Delete AVD
    if "$AVDMANAGER" list avd -c 2>/dev/null | grep -q "^${AVD_NAME}$"; then
        echo "Deleting AVD $AVD_NAME..."
        "$AVDMANAGER" delete avd -n "$AVD_NAME" 2>/dev/null || true
    fi

    # Stop WireMock
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

# Wait for WireMock to be ready
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

# ─── Step 2: Ensure system image is installed ───
echo ""
echo "=== Ensuring Android system image ==="
if [ ! -d "$ANDROID_SDK/system-images/android-35/google_apis" ]; then
    echo "Installing $SYSTEM_IMAGE..."
    yes | "$SDKMANAGER" "$SYSTEM_IMAGE" || true
fi

# ─── Step 3: Create headless emulator ───
echo ""
echo "=== Creating headless emulator ==="

# Delete existing AVD if present
"$AVDMANAGER" delete avd -n "$AVD_NAME" 2>/dev/null || true

echo "no" | "$AVDMANAGER" create avd \
    -n "$AVD_NAME" \
    -k "$SYSTEM_IMAGE" \
    -d "pixel_6" \
    --force

# ─── Step 4: Boot emulator ───
echo ""
echo "=== Booting emulator (headless) ==="

# Use a dedicated port to avoid conflicts with other running emulators
EMU_PORT=5584
EMULATOR_SERIAL="emulator-${EMU_PORT}"

"$EMULATOR" -avd "$AVD_NAME" \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    -no-snapshot \
    -wipe-data \
    -port "$EMU_PORT" &
EMULATOR_PID=$!

echo "Waiting for emulator to boot (PID $EMULATOR_PID, serial $EMULATOR_SERIAL)..."

# Wait for emulator to register with adb
for i in $(seq 1 60); do
    if "$ADB" -s "$EMULATOR_SERIAL" get-state 2>/dev/null | grep -q "device"; then
        break
    fi
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        echo "❌ Emulator process died."
        exit 1
    fi
    if [ "$i" -eq 60 ]; then
        echo "❌ Emulator did not register with adb in time."
        exit 1
    fi
    sleep 2
done

# Wait for boot_completed
echo "Waiting for boot to complete..."
for i in $(seq 1 120); do
    BOOT=$("$ADB" -s "$EMULATOR_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$BOOT" = "1" ]; then
        echo "Emulator booted."
        break
    fi
    if [ "$i" -eq 120 ]; then
        echo "❌ Emulator boot timed out."
        exit 1
    fi
    sleep 2
done

# Dismiss any setup wizards and prepare Chrome
"$ADB" -s "$EMULATOR_SERIAL" shell pm disable-user --user 0 com.google.android.setupwizard 2>/dev/null || true
"$ADB" -s "$EMULATOR_SERIAL" shell input keyevent KEYCODE_HOME 2>/dev/null || true

# Disable animations for more reliable test execution
"$ADB" -s "$EMULATOR_SERIAL" shell settings put global window_animation_scale 0 2>/dev/null || true
"$ADB" -s "$EMULATOR_SERIAL" shell settings put global transition_animation_scale 0 2>/dev/null || true
"$ADB" -s "$EMULATOR_SERIAL" shell settings put global animator_duration_scale 0 2>/dev/null || true

# ─── Step 5: Build and install app ───
echo ""
echo "=== Building and installing mock flavor ==="
./gradlew :sample-android:assembleMockDebug 2>&1 | tail -5

# Install on our specific emulator only
APK_PATH="sample-android/build/outputs/apk/mock/debug/sample-android-mock-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at $APK_PATH"
    exit 1
fi
"$ADB" -s "$EMULATOR_SERIAL" install -r "$APK_PATH"

# Verify install
if ! "$ADB" -s "$EMULATOR_SERIAL" shell pm list packages | grep -q "dev.bmcreations.phantom.connect.sample"; then
    echo "❌ App not installed."
    exit 1
fi
echo "App installed on $EMULATOR_SERIAL."

# ─── Step 6: Run Maestro E2E tests ───
echo ""
echo "=== Running Maestro E2E tests ==="
echo "Flow: $FLOW"
echo "Device: $EMULATOR_SERIAL"

# Set ANDROID_SERIAL so Maestro and adb target the right device
export ANDROID_SERIAL="$EMULATOR_SERIAL"
set +e
maestro --device "$EMULATOR_SERIAL" test "$FLOW"
TEST_EXIT=$?
set -e

# Capture logcat for debugging — get full crash stack trace
echo ""
echo "=== App logs (errors/crashes) ==="
"$ADB" -s "$EMULATOR_SERIAL" logcat -d -s AndroidRuntime:E | tail -60 || true

if [ $TEST_EXIT -eq 0 ]; then
    echo ""
    echo "✅ E2E tests passed!"
else
    echo ""
    echo "❌ E2E tests failed (exit code $TEST_EXIT)."
    # Copy debug artifacts
    LATEST_TEST=$(ls -td "$HOME/.maestro/tests/"* 2>/dev/null | head -1)
    if [ -n "$LATEST_TEST" ]; then
        echo "Debug artifacts: $LATEST_TEST"
    fi
fi

exit $TEST_EXIT
