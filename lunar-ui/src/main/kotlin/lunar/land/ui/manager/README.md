# App Manager Module

This module handles fetching and processing app information in a clean, efficient manner.

## Architecture

### Components

1. **AppManager** (`AppManager.kt`)
   - Main manager class responsible for fetching and processing apps
   - Uses Kotlin Coroutines and Flow for reactive, asynchronous operations
   - Processes apps in parallel for optimal performance
   - Handles resource-intensive operations (icon loading) efficiently

2. **AppInfo** (`model/AppInfo.kt`)
   - Data model representing complete app information
   - Includes app metadata, icon, and generated color
   - Immutable data class for thread safety

3. **AppManagerProvider** (`AppManagerProvider.kt`)
   - Composable function for providing AppManager instance
   - Ensures proper context usage in Compose

## Features

- **Parallel Processing**: Apps are processed concurrently using `async`/`awaitAll`
- **Flow-based**: Uses Kotlin Flow for reactive data streams
- **Color Extraction**: Extracts dominant colors from app icons using Android Palette API
- **Error Handling**: Gracefully handles errors without crashing
- **Resource Management**: All heavy operations run on IO dispatcher
- **Clean Architecture**: Separation of concerns with dedicated manager layer

## Components

### ColorExtractor
- Extracts dominant colors from app icons
- Uses Android Palette API for accurate color extraction
- Falls back to generated color if extraction fails
- Processes colors on background thread for performance

## Usage

```kotlin
// In ViewModel
val appManager = AppManager(context)
appManager.getAllApps()
    .collect { apps ->
        // Handle apps
    }

// In Composable
val appManager = rememberAppManager()
```

## Best Practices

- All resource-intensive operations run on `Dispatchers.IO`
- Parallel processing for better performance
- Immutable data models for thread safety
- Proper error handling with try-catch blocks
- Flow-based reactive programming

