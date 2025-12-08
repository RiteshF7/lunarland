# App Drawer Components

A modular Kotlin Compose implementation of a 3D-styled app drawer that matches the HTML UI design.

## Structure

```
appdrawer/
├── AppItemData.kt        # Data model for app items
├── AppItem.kt            # Individual app button component
├── AppRow.kt             # Row container component
├── AppDrawerContainer.kt # Main container component
├── AppDrawerScreen.kt    # Example usage screen
└── README.md             # This file
```

## Components

### Colors
Colors are generated dynamically using the app's default color system (`rememberAppColor`). Each app gets a consistent color based on its name hash, which is then adjusted for theme compatibility.

### AppItemData
Data class representing an app item with:
- `name`: Display name
- `icon`: ImageVector icon
- `backgroundColor`: Button background color
- `textColor`: Text and icon color
- `glowColor`: Glow effect color
- `isWide`: Whether item spans 75% width

### AppItem
The main app button component with:
- 3D transform effects (rotation and translation)
- Gradient background
- Glow effects
- Smooth animations
- Hover and press states

### AppRow
Container for arranging app items in a row with consistent spacing.

### AppDrawerContainer
Main container with theme background (MaterialTheme.colorScheme.background), centered layout, and max width of 600dp.

## Usage

1. Copy the entire `appdrawer` package to your project
2. Import the components in your screen:

```kotlin
import com.example.appdrawer.*

@Composable
fun MyScreen() {
    AppDrawerScreen() // Or use components individually
}
```

3. Customize icons and colors as needed

## Dependencies

- `androidx.compose.foundation`
- `androidx.compose.material3`
- `androidx.compose.ui`
- `androidx.compose.animation`

## Features

- ✅ Theme-aware colors using app's default color system
- ✅ 3D transform effects
- ✅ Smooth animations
- ✅ Glow effects
- ✅ Gradient backgrounds
- ✅ Responsive layout
- ✅ Modular and reusable components
