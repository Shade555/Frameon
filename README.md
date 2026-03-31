# Frameon - Professional Gallery & Security Suite

Frameon is a high-performance Android gallery app built with **Clean Architecture** and **Jetpack Compose**. It blends standard media management with advanced AI-driven security and creative tools.

## 🚀 Key Features

### 1. Smart Media Gallery
*   **Real-time Synchronization:** Uses `ContentObserver` and `callbackFlow` to instantly reflect new photos or downloads without refreshing.
*   **Temporal Grouping:** Automatically organizes media into elegant sections by Month and Year.
*   **Unified Media Support:** Seamlessly handles images, videos, and downloads in a single, performant grid.

### 2. AI-Driven Security (Pro-Active Protection)
*   **Background OCR Scanning:** Leverages **Google ML Kit** to scan new images for sensitive data (passwords, SSNs, credit cards) even when the app is completely closed.
*   **Spatial Analysis:** Uses geometric logic to link labels (e.g., "Password:") to their corresponding values, reducing false positives.
*   **Luhn Validation:** Validates 16-digit numbers against banking algorithms to accurately identify credit cards.
*   **Urgent Alerts:** Triggers high-priority notifications with custom vibration patterns to warn users of privacy leaks.

### 3. Biometric Secure Folder
*   **Encrypted Logic:** Moves sensitive media from public Scoped Storage to the app's **private internal directory**, making them invisible to all other apps.
*   **Smart Auth:** Integrated with **Android Biometrics**. Long-press the app title to enter; if no phone lock is set, it allows direct access.
*   **Ghost-Copy Prevention:** Implements professional `MediaStore` deletion requests to ensure no "ghost" duplicates remain in the public gallery.

### 4. Creative Suite
*   **Collage Creator:** Supports multi-selection and real-time previewing.
*   **Custom Geometry:** Uses `GenericShape` to clip photos into Squares, Circles, or complex Stars.
*   **Direct Export:** Saves processed collages directly back to a dedicated "Frameon" folder in the system gallery.

## 🛠 Hurdles Overcome

*   **Scoped Storage Restrictions:** Solved the "cannot delete files created by other apps" issue by implementing `MediaStore.createDeleteRequest` with a user-friendly explanation dialog.
*   **Background Persistence:** Solved the "app closed" detection problem using **WorkManager Content URI Triggers**, allowing the OS to wake the app for security scans.
*   **Notification Reliability:** Overcame Android's notification channel caching by implementing versioned Channel IDs to force update vibration and importance settings.
*   **OCR Geometry:** Fixed the "Password on new line" detection by flattening text blocks and calculating physical proximity between text elements.

## 🏗 Tech Stack
*   **Language:** Kotlin (Coroutines, Flow)
*   **UI:** Jetpack Compose (Material 3)
*   **DI:** Hilt (Dagger)
*   **Async:** WorkManager
*   **AI:** Google ML Kit (Bundled OCR)
*   **Image Loading:** Coil
