# Chengying - Android Desktop Mode

Chengying is an Android application that transforms your device into a desktop workstation when connected to an external display (such as USB-C monitors or AR glasses).

## Key Features

- **Desktop Environment on External Display**: Projects a desktop interface to your connected monitor or AR glasses.
- **Phone as Touchpad Controller**: Phone screen turns black and displays 3 operation buttons, converting touch input into mouse events on the external display.
- **Efficient and Private**: Saves battery by dimming the phone screen while providing privacy for AR glasses users.
- **Landscape Fullscreen Apps**: Launches applications in landscape, fullscreen mode on the external display.

## Setup Requirements

1. **Accessibility Permissions**: Enable accessibility for Chengying in system settings.
2. **Developer Options**: 
   - Enable "Force desktop mode" 
   - Enable "Freeform window mode" and "Force activities to be resizable" (may be required)
3. **Shizuku Permission**: Install Shizuku and grant permissions to Chengying.
4. **HyperOS Specific**: Disable system optimization to allow landscape/fullscreen app launching.

## How to Use

1. Connect your external display via USB-C.
2. Launch Chengying and select your connected display.
3. Phone screen will switch to touchpad mode with 3 operation buttons.
4. Use the phone's touchpad to control the mouse cursor on the external display.
5. Launch apps from the desktop interface - they will open in landscape fullscreen mode.

## Technical Notes

- Built with Kotlin and Jetpack Compose (Material 3)
- Uses Android's Presentation API for secondary display UI
- Implements MVVM architecture with Hilt dependency injection

## Build Requirements

- Android Studio with Kotlin support
- Shizuku installed on target device
- External display for testing

## Status

Functional features include display detection, desktop projection, touchpad control, and app launching on the secondary screen.

## License

