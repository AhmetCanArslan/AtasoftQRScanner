# Atasoft QR Scanner

## Overview

This Android application serves as a QR code scanner designed specifically for managing entry at the AI Summit Erzurum event. It acts as the frontend component for the data management system, integrating with the backend provided by the [AtasoftDataExtractor](https://github.com/AhmetCanArslan/AtasoftDataExtractor) project.

## Functionality

1.  **QR Code Scanning:** Uses the device camera to scan QR codes presented by attendees.
2.  **Firebase Integration:**
    *   Connects to a Firebase Firestore database to verify the scanned QR code (UUID) against registered attendee data.
    *   Retrieves attendee information (Name, Email, Visit Count) upon successful validation.
    *   Increments the `Counter` field in Firestore for the specific attendee upon successful entry ("Accept").
3.  **User Interface:**
    *   Displays the camera preview for scanning.
    *   Shows attendee information on a separate screen after a successful scan.
    *   Provides "Accept" and "Reject" options for event staff.
    *   Handles camera permissions and displays relevant error or status messages.

## Technology Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose
*   **Camera:** CameraX
*   **QR Code Recognition:** ML Kit Barcode Scanning
*   **Database:** Firebase Firestore (optional, depends on `google-services.json` presence)

## Setup

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  **Firebase Setup (Optional but Recommended):**
    *   Create a Firebase project.
    *   Set up a Firestore database. Ensure your Firestore rules allow reading the `users` collection and updating the `Counter` field (see `firestore.rules` for an example).
    *   Add an Android app to your Firebase project with the package name `com.canceng.atasoftqrscanner`.
    *   Download the `google-services.json` file from your Firebase project settings.
    *   Place the `google-services.json` file in the `app/` directory of this project.
4.  Build and run the application on an Android device.

## Connection to AtasoftDataExtractor

This QR Scanner application reads the data populated and managed by the [AtasoftDataExtractor](https://github.com/AhmetCanArslan/AtasoftDataExtractor) project. The `AtasoftDataExtractor` is responsible for collecting attendee information (e.g., from Google Forms) and storing it in the Firebase Firestore database, which this app then accesses for validation and updates.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
