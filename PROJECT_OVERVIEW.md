# Project Chengying - Android Desktop Mode

## 1. Overview
**Chengying** is an Android application designed to transform an Android device into a desktop workstation when connected to an external display (via USB-C or Miracast). 

When an external display is detected:
1.  The external display shows a desktop environment (`SecondScreenPresentation`).
2.  The phone's internal screen turns into a trackpad/controller.
3.  Users can launch installed apps onto the external display.
4.  Input events (mouse cursor) are injected into the system using **Shizuku** to allow interaction with the external display.

## 2. Technical Stack
*   **Language**: Kotlin 2.0.21 (leveraging K2 compiler features).
*   **UI Framework**: Jetpack Compose (Material 3). **No XML**.
*   **Architecture**: Clean Architecture + MVVM (Unidirectional Data Flow).
*   **DI**: Hilt.
*   **Async**: Coroutines + Flow.
*   **System Integration**: 
    *   `android.app.Presentation` for the secondary display UI.
    *   **Shizuku** (root/adb) for `InputManager` injection (simulating mouse events).
    *   `ActivityOptions.setLaunchDisplayId` for multi-display app launching.

## 3. Architecture & Data Flow

### Layered Structure
*   **`ui/`**: Compose screens and ViewModels.
    *   `home/`: The main phone screen UI (Display list & Touchpad).
    *   `presentation/`: The code running on the external monitor (`SecondScreenPresentation`).
*   **`domain/`**: Pure Kotlin business logic.
    *   `model/`: Data classes (`AppInfo`, `ExternalDisplay`, `CursorState`).
    *   `repository/`: Interfaces (`DisplayRepository`, `AppRepository`, `CursorRepository`).
*   **`data/`**: Implementation details.
    *   `repository/`: Repository implementations.
    *   `source/`: Data sources (e.g., `ShizukuInputManager`).

### Key Flows
1.  **Display Detection**:
    *   `MainActivity` listens for `DISPLAY_SERVICE` changes.
    *   `DisplayRepository` emits a list of `ExternalDisplay`.
    *   `HomeViewModel` observes this list.
2.  **Desktop Activation**:
    *   When a display is selected, `PresentationRepository.showPresentation()` is called.
    *   `SecondScreenPresentation` is attached to the external `Display` context.
    *   `MainActivity` switches to "Touchpad Mode" (hides system bars, locks orientation).
3.  **Input Simulation**:
    *   User pans on `TouchpadScreen` (Phone).
    *   `HomeViewModel` receives deltas -> calls `CursorRepository`.
    *   `CursorRepository` updates local cursor state (for UI feedback on the external screen) AND calls `ShizukuInputManager`.
    *   `ShizukuInputManager` uses hidden Android APIs (`IInputManager.injectInputEvent`) to move the system cursor or simulate clicks on the specific display ID.
4.  **App Launching**:
    *   User clicks an app icon on `SecondScreenPresentation`.
    *   `AppRepository.launchApp` is called with `displayId`.
    *   Uses `ActivityOptions.makeBasic().setLaunchDisplayId(displayId)` to start the app on the secondary screen.

## 4. Key Components

### `MainActivity.kt`
*   Entry point.
*   Handles `com.w3n9.chengying.DISPLAY_CONNECTION_CHANGED` (though currently logic seems to rely partly on Repository state).
*   Manages "Touchpad Mode" (immersive mode, landscape).

### `SecondScreenPresentation.kt`
*   Extends `android.app.Presentation`.
*   Hosts the "Desktop" UI using Jetpack Compose (`ComposeView`).
*   Displays the wallpaper, app grid, and a custom software cursor (drawn on a Canvas overlay).
*   **Note**: The custom cursor is primarily for visual feedback; actual input is handled via system injection to interact with other apps.

### `ShizukuInputManager.kt`
*   **Critical Component**: Bypasses Android's restriction on injecting touch/mouse events.
*   Requires Shizuku permission.
*   Uses reflection to access `android.hardware.input.IInputManager`.
*   Injects `MotionEvent` with the correct `displayId`.

### `Repositories`
*   **`AppRepositoryImpl`**: Queries `PackageManager` for launchable apps; launches intents with specific flags (`FLAG_ACTIVITY_MULTIPLE_TASK` | `FLAG_ACTIVITY_NEW_TASK`).
*   **`DisplayRepositoryImpl`**: Filters and provides connected displays.
*   **`CursorRepositoryImpl`**: Shared state for cursor position (x, y) between the Input source (Phone) and Output view (Presentation).

## 5. Current Implementation Status
*   **Display**: Functional. Can detect and project to external screens.
*   **Input**: Functional (via Shizuku). Touchpad on phone moves cursor on desktop.
*   **Launcher**: Basic grid of installed apps.
*   **UI**: Material 3 styling. Simple "Chengying OS" branding.

## 6. How to Build & Run
1.  Ensure **Shizuku** is installed and running on the target device.
2.  Grant Shizuku permission to Chengying upon first launch.
3.  Connect an external monitor (or use "Simulate secondary display" in Developer Options).
4.  Select the display from the list on the phone.
5.  Phone screen goes black (Touchpad); External screen shows Desktop.

## 7. Next Steps (Roadmap)
1.  **UI Polish**: Enhance Material You implementation.
2.  **Configuration**: Allow toggling between "Mirror" and "Desktop" modes.
3.  **Window Management**: Better handling of multi-window scenarios if possible.
4.  **Stability**: Handle display disconnections more gracefully.
