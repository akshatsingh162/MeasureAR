MeasureAR – AR & Sensor Toolkit

MeasureAR is a multi-functional Android application that combines Augmented Reality (AR) and smartphone sensors to provide seven practical measurement and utility tools in a single application.

Built using Kotlin and Google ARCore, the application enables users to measure real-world distances, heights, and areas through their smartphone camera while also providing sensor-powered utilities such as a digital level, ruler, compass, and light meter.

Features

MeasureAR offers seven tools divided into two categories.

1. AR Measurement Tools

Powered by Google ARCore and real-time 3D coordinate tracking.

Tool

Description

Distance Measurement

Measures the distance between two user-selected points in the real world using ARCore hit testing and 3D coordinates.

Height Measurement

Calculates vertical distance between two selected points using ARCore's gravity-aligned coordinate system.

Area Measurement

Allows users to select up to 12 corners of a surface and calculates its area and perimeter.

AR Capabilities:

Real-time camera feed rendering.

Horizontal plane detection.

ARCore hit testing and 3D point selection.

World-coordinate-based distance calculations.

Visual measurement markers and connecting lines.

Live measurement updates.

Device ARCore compatibility checks.

2. Sensor Tools

Uses built-in Android device sensors to provide additional utilities.

Tool

Description

Digital Level

Uses device orientation and gravity sensors to determine whether a surface or phone is level. Includes calibration and reset functionality.

Ruler

Provides an on-screen ruler with centimetre and inch scales, draggable measurement markers, and manual calibration.

Compass

Uses rotation sensor data to display magnetic heading and magnetic field strength, with calibration warnings.

Light Meter

Uses the ambient light sensor to estimate illumination in lux and displays minimum, average, and maximum readings.

Technology Stack

Category

Technologies

Language

Kotlin

IDE

Android Studio

Platform

Android SDK

Augmented Reality

Google ARCore

Graphics Rendering

OpenGL ES 2.0

UI Development

Native Android UI

Device Integration

Android Sensor Framework

AR Tracking

Plane Detection, Hit Testing, 3D World Coordinates

Build System

Gradle

How It Works

AR Measurement

The AR measurement tools use the device camera and ARCore to understand the surrounding environment.

The application initializes an ARCore session.

The camera feed is rendered in real time.

ARCore detects suitable surfaces and tracks the device's position.

Users select measurement points on detected surfaces.

The selected points are represented using 3D world coordinates.

The application calculates the required measurement and displays the result.

Distance measurements use the Euclidean distance between two 3D points:

[
d = \sqrt{(x_2-x_1)^2+(y_2-y_1)^2+(z_2-z_1)^2}
]

Height measurements use the vertical difference between selected points.

Area measurements use the selected polygon corners to calculate the enclosed area and perimeter.

Sensor-Based Utilities

The sensor tools interact with Android's Sensor Framework to access available hardware sensors.

Depending on the tool, sensor readings are processed to provide orientation, magnetic heading, gravity-based leveling, and ambient illumination information.

Installation & Setup

Prerequisites

Android Studio.

Android device running Android 7.0 (API 24) or later.

ARCore-compatible device for AR measurement features.

Camera permission for AR functionality.

Required hardware sensors for individual sensor tools.

Clone the Repository

git clone https://github.com/YOUR_USERNAME/MeasureAR.git

Navigate to the project directory:

cd MeasureAR

Open in Android Studio

Open Android Studio.

Select Open and choose the cloned project directory.

Allow Gradle to sync.

Connect an Android device.

Enable USB debugging if required.

Build and run the application.

The project uses the Google ARCore dependency:

implementation("com.google.ar:core:1.56.0")

ARCore availability is checked at runtime. Devices without ARCore support can still use the available sensor-based tools.

Project Structure

The application is organised around Android UI components, AR rendering, and device sensor functionality.

Key implementation areas include:

MainActivity: Application entry point and primary interface.

ARView: AR rendering view, touch interactions, and display geometry handling.

BackgroundRenderer: OpenGL ES camera background rendering and AR measurement visualization.

ARCore Integration: Session management, plane detection, hit testing, and world-coordinate tracking.

Sensor Tools: Individual utilities that interact with Android device sensors.

Resources: Layouts, drawables, colours, strings, and UI styling.

Refer to the source directories for the complete implementation.

Limitations

MeasureAR relies on smartphone hardware and environmental conditions. Results should be treated as estimates rather than precision surveying measurements.

AR Measurements: Accuracy depends on camera tracking, surface detection, lighting, and point placement.

Height Measurement: Requires selectable points; it cannot automatically detect every physical edge.

Area Measurement: Best suited to approximately flat surfaces. Significant height differences between corners can make the result unreliable.

Digital Level: Depends on the accuracy of the device's motion sensors and calibration.

Ruler: Screen DPI varies between devices, so manual calibration is recommended.

Compass: Displays magnetic north rather than true north and can be affected by nearby metal or magnets.

Light Meter: Ambient light sensor readings are approximate and are not a substitute for a dedicated lux meter.

Future Improvements

Potential areas for further development include:

Measurement history and saved measurements.

Screenshot and measurement export functionality.

Improved AR point-placement feedback.

Enhanced measurement stability and accuracy.

Additional measurement modes.

Better onboarding and interactive tutorials.

Expanded device compatibility testing.

UI and performance optimisations.

Project Goals

MeasureAR was developed to explore the practical integration of Augmented Reality, 3D coordinate systems, OpenGL rendering, and smartphone hardware sensors within a native Android application.

The project focuses on combining multiple real-world utilities into a single, accessible mobile toolkit while exploring the capabilities and limitations of modern smartphone hardware.
