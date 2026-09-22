# EyeSwipe

Detects when you tilt your head/eyes upward (via the front camera) and
triggers an upward swipe gesture on whatever app is currently open — e.g.
skipping to the next TikTok/Reels/Shorts video.

## How it works
- `EyeTrackingService` — a foreground Service that runs the front camera
  through CameraX (no preview shown) and feeds each frame to Google ML Kit's
  on-device Face Detector, which is free and runs fully offline. It reads
  `headEulerAngleX` (pitch — positive = looking up). When it crosses your
  chosen threshold, it fires a swipe.
- `SwipeAccessibilityService` — an AccessibilityService that actually
  performs the swipe via `dispatchGesture`, since that's the only Android API
  that can inject a touch gesture into a different app.
- `MainActivity` — lets you grant the camera permission, open Settings to
  enable the accessibility service, tune sensitivity, and start/stop tracking.

**This tracks head tilt, not pupil-only gaze.** True eye-only gaze tracking
(head held still, camera reading where the pupils point) is unreliable on a
phone's front camera. Tilting your head/face upward is what similar
hands-free-scroll apps actually use, and it's far more robust.

## Building it
1. Install [Android Studio](https://developer.android.com/studio) (free).
2. `File > Open` and select the `EyeSwipe` folder.
3. Let Gradle sync. If it flags a library version as outdated, accept
   Android Studio's suggested upgrade — I picked recent stable versions but
   couldn't compile this myself to verify against the very latest releases.
4. Connect your phone via USB with USB debugging on, or build
   `Build > Build Bundle(s)/APK(s) > Build APK(s)` and copy the APK to your
   phone (enable "Install unknown apps" for whatever app you use to open it).

## Using it
1. Open the app, tap **Grant camera permission**.
2. Tap **Open Accessibility settings**, find "EyeSwipe" in the list, and
   turn it on (Android requires this to be done manually — no app can
   enable it for itself).
3. Adjust the sensitivity slider — this is the angle you need to tilt your
   head up past before a swipe fires. Start high (~20°+) and lower it if
   swipes don't trigger easily enough.
4. Tap **Start tracking**, then open TikTok/Instagram and tilt your head up.

## Things to know before relying on this
- **The camera-in-use indicator dot will always be visible** while tracking
  runs — Android shows this by design for any active camera, and it cannot
  be hidden.
- **Battery drain is real.** Continuous camera capture + on-device ML is
  not free. Expect noticeably faster battery use while tracking is on.
- **Background kill-happy phones** (MIUI/Xiaomi, Samsung, Huawei, OnePlus,
  etc.) may kill the service to save battery unless you manually exclude the
  app from battery optimization in system Settings.
- **Not distributable via Play Store as-is** — Play policy requires
  Accessibility Service usage to serve users with disabilities and be
  clearly disclosed as such; this is fine for personal sideloading but you
  would need to adjust scope/disclosure to publish it.
- **Calibration**: `headEulerAngleX` accuracy varies by lighting, camera
  angle, and how you normally hold the phone. If it's too twitchy or never
  triggers, that's the threshold slider to tune — there's no universal
  "correct" number.
