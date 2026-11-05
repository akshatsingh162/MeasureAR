# MeasureAR – AR & Sensor Toolkit

**A multi-functional Android application combining Augmented Reality and smartphone sensors to turn your phone into a measurement toolkit.**

MeasureAR is a Kotlin-based Android application featuring **7 practical tools** across AR-based measurement and sensor-powered utilities. It uses Google ARCore for real-world measurements and the Android Sensor Framework for device-based utilities.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
  - [AR Measurement Tools](#1-ar-measurement-tools)
  - [Sensor Tools](#2-sensor-tools)
- [Technology Stack](#technology-stack)
- [Technical Implementation](#technical-implementation)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Device Compatibility](#device-compatibility)
- [Limitations](#limitations)
- [Future Improvements](#future-improvements)

---

## Overview

MeasureAR brings together augmented reality, 3D coordinate tracking, OpenGL rendering, and smartphone hardware sensors in a single native Android application.

Instead of relying on separate applications for everyday measurement utilities, users can access seven tools through a unified interface.

The application is divided into two main categories:

- **AR Measure:** Distance, Height, and Area.
- **Sensor Tools:** Level, Ruler, Compass, and Light Meter.

## Features

### 1. AR Measurement Tools

Powered by **Google ARCore**, these tools use camera-based environmental tracking and 3D world coordinates.

| Tool | Description |
|---|---|
| **Distance** | Measures the straight-line distance between two selected points in real-world space. |
| **Height** | Calculates the vertical difference between two selected points using the gravity-aligned coordinate system. |
| **Area** | Calculates the area and perimeter of a surface using up to 12 user-selected corners. |

#### Key AR Capabilities

- Real-time camera feed rendering.
- Horizontal plane detection.
- ARCore hit testing.
- 3D world-coordinate tracking.
- Interactive point placement.
- Visual measurement markers and connecting lines.
- Real-time measurement calculations.
- ARCore device compatibility checks.

### 2. Sensor Tools

Powered by the **Android Sensor Framework**, these utilities make use of available smartphone hardware sensors.

| Tool | Description |
|---|---|
| **Level** | A digital spirit level using gravity and orientation sensor data, with calibration and reset functionality. |
| **Ruler** | An on-screen ruler featuring centimetre and inch scales, draggable measurement markers, and manual calibration. |
| **Compass** | Displays magnetic heading and magnetic field strength using rotation sensor data. |
| **Light Meter** | Estimates ambient illumination in lux and displays minimum, average, and maximum readings. |

---

## Technology Stack

| Category | Technologies |
|---|---|
| Programming Language | Kotlin |
| Development Environment | Android Studio |
| Platform | Android SDK |
| Augmented Reality | Google ARCore |
| Graphics Rendering | OpenGL ES 2.0 |
| UI Development | Native Android UI |
| Device Integration | Android Sensor Framework |
| AR Processing | Plane Detection, Hit Testing, 3D Coordinates |
| Build System | Gradle |

## Technical Implementation

### Augmented Reality and 3D Measurement

The AR measurement system uses Google ARCore to track the device's position and detect real-world surfaces.

The general measurement workflow is:

1. Initialize an ARCore session.
2. Render the live camera feed using OpenGL ES.
3. Detect horizontal planes in the environment.
4. Perform hit testing when the user selects a point.
5. Obtain the selected point's 3D world coordinates.
6. Calculate the required measurement.
7. Render visual markers and measurement indicators.

**Distance Calculation**

The straight-line distance between two 3D points is calculated using the Euclidean distance formula:

$$
d = \sqrt{(x_2-x_1)^2+(y_2-y_1)^2+(z_2-z_1)^2}
$$

**Height Calculation**

Height measurement uses the vertical difference between two selected points along ARCore's gravity-aligned Y-axis:

$$
h = |y_2-y_1|
$$

**Area Calculation**

Area measurement uses the selected corner coordinates to calculate the enclosed polygon area and perimeter.

### Sensor Integration

The sensor utilities access available hardware sensors through Android's `SensorManager` APIs.

Depending on the tool, sensor readings are processed to determine:

- Device orientation and tilt.
- Gravity-based leveling.
- Magnetic heading.
- Magnetic field strength.
- Ambient light intensity.

The application also includes calibration functionality where appropriate to improve usability across different devices.

---

## Project Structure

The project follows a modular Android application structure.

```text
MeasureAR/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/akshat/measurear/
│           │       ├── MainActivity.kt
│           │       ├── ARView.kt
│           │       ├── BackgroundRenderer.kt
│           │       └── ...
│           │
│           ├── res/
│           │   ├── drawable/
│           │   ├── layout/
│           │   ├── values/
│           │   └── ...
│           │
│           └── AndroidManifest.xml
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

*Note: This is an illustrative overview. Individual source files and directories may differ in the current implementation.*

---

## Getting Started

### Prerequisites

- Android Studio.
- Android SDK.
- An Android device running Android 7.0 (API 24) or later.
- An ARCore-compatible device for AR measurement features.
- Required hardware sensors for individual sensor utilities.

### 1. Clone the Repository

```bash
git clone https://github.com/akshatsingh162/MeasureAR
```

### 2. Open the Project

Open the cloned project directory in Android Studio.

### 3. Sync Gradle

Allow Android Studio to download the required dependencies and complete Gradle synchronization.

The project uses the following ARCore dependency:

```kotlin
implementation("com.google.ar:core:1.56.0")
```

### 4. Connect an Android Device

- Enable Developer Options.
- Enable USB Debugging.
- Connect your Android device.
- Ensure Google Play Services for AR is installed and updated if using AR features.

### 5. Build and Run

Select your connected device and click **Run** in Android Studio.

Grant the required permissions when prompted.

---

## Device Compatibility

MeasureAR supports two categories of functionality with different hardware requirements.

| Functionality | Requirement |
|---|---|
| Distance, Height, Area | ARCore-compatible device |
| Level | Compatible motion/gravity sensors |
| Ruler | Screen display and manual calibration |
| Compass | Compatible rotation/magnetic sensors |
| Light Meter | Ambient light sensor |

ARCore availability is checked at runtime. Devices without ARCore support can still access compatible sensor-based tools.

---

## Limitations

Measurement results depend on device hardware, environmental conditions, and sensor quality.

| Tool | Known Limitations |
|---|---|
| AR Measurement | Accuracy depends on tracking quality, lighting, surface detection, and point placement. |
| Height | Requires selectable points and cannot automatically detect every physical edge. |
| Area | Best suited to approximately flat surfaces. Significant height differences between selected corners can make results unreliable. |
| Level | Depends on sensor accuracy and calibration. |
| Ruler | Screen DPI varies across devices; manual calibration is recommended. |
| Compass | Indicates magnetic north rather than true north and can be affected by nearby metal or magnets. |
| Light Meter | Provides approximate illumination readings and is not a replacement for a dedicated lux meter. |

**Note:** MeasureAR is intended for everyday estimation and utility purposes, not professional surveying or precision measurement.

---

## Future Improvements

- [ ] Measurement history and saved measurements.
- [ ] Screenshot and measurement export functionality.
- [ ] Improved AR point-placement feedback.
- [ ] Enhanced measurement stability.
- [ ] Additional measurement modes.
- [ ] Interactive onboarding tutorial.
- [ ] Expanded device compatibility testing.
- [ ] UI and performance optimisations.

---

**MeasureAR – Measure your world, one point at a time.**
