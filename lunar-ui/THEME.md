# Lunar Land Theme Documentation

## Overview

The Lunar Land app uses a centralized theme system defined in `LunarTheme.kt`. This ensures visual consistency across all screens and components. The theme is based on the TaskExecutorAgentScreen design with a modern, minimal aesthetic featuring a black background and vibrant green accent color.

## Single Source of Truth

**Location:** `lunar-ui/src/main/kotlin/lunar/land/ui/core/theme/LunarTheme.kt`

All theme constants should be imported and used from this file:
```kotlin
import lunar.land.ui.core.theme.LunarTheme
```

## Color Palette

### Primary Colors

- **AccentColor** (`#4DFF88`)
  - Vibrant green used for active states, highlights, and accents
  - Used in: buttons, borders, indicators, sphere visualizer

- **BackgroundColor** (`#000000`)
  - Pure black - main background color
  - Used for: screen backgrounds

- **SecondaryBackgroundColor** (`#0a0f0a`)
  - Very dark green-tinted black
  - Used for: panels, cards, elevated surfaces

- **InactiveBackgroundColor** (`#1a1f1a`)
  - Dark green-tinted gray
  - Used for: inactive buttons, input fields, disabled states

- **BorderColor** (`#2a3a2a`)
  - Dark green-tinted gray
  - Used for: borders around cards, buttons, input fields

### Text Colors

- **TextPrimary** (`White`)
  - Main text color for primary content

- **TextSecondary** (`White` with 60% opacity)
  - Secondary text, placeholders, inactive text

- **TextTertiary** (`White` with 40% opacity)
  - Hints, placeholders, subtle text

- **TextDisabled** (`White` with 40% opacity)
  - Disabled states

## Typography

### Font Family

**Manrope** - The primary font family used throughout the app
- Resource: `R.font.manrope_variable`
- Weight: Normal (variable font supports multiple weights)

### Text Styles

All text styles use the Manrope font family and are defined in `LunarTheme.Typography`:

- **DisplayLarge** - 32sp, SemiBold
  - Page titles and major headings

- **DisplayMedium** - 24sp, SemiBold
  - Section headings

- **DisplaySmall** - 20sp, SemiBold
  - Subsection headings

- **BodyLarge** - 16sp, Medium
  - Important body text

- **BodyMedium** - 14sp, Medium
  - Standard body text and button labels

- **BodySmall** - 12sp, Medium
  - Secondary information and captions

- **Button** - 15sp, SemiBold
  - Button labels (color: AccentColor)

- **Input** - 14sp, Medium
  - Text input fields

- **Placeholder** - 14sp, Medium
  - Input placeholders (color: TextTertiary)

## Spacing

Consistent spacing scale defined in `LunarTheme.Spacing`:

- **ExtraSmall** - 4dp
- **Small** - 8dp
- **Medium** - 12dp
- **Large** - 16dp
- **ExtraLarge** - 20dp
- **XXLarge** - 24dp
- **XXXLarge** - 32dp

## Corner Radius

Consistent corner radius scale defined in `LunarTheme.CornerRadius`:

- **Small** - 8dp
  - Small buttons and badges

- **Medium** - 12dp
  - Standard buttons, cards, input fields

- **Large** - 16dp
  - Large panels and containers

## Border Width

- **BorderWidth** - 1dp
  - Standard border width for all borders

## Alpha Values

Transparency values for consistent effects, defined in `LunarTheme.Alpha`:

- **High** - 0.3f
  - Active borders and strong highlights

- **Medium** - 0.15f
  - Active backgrounds and moderate highlights

- **Low** - 0.08f
  - Subtle backgrounds and gentle highlights

- **VeryLow** - 0.05f
  - Very subtle effects

## Usage Examples

### Colors

```kotlin
// Background
Box(
    modifier = Modifier.background(LunarTheme.BackgroundColor)
)

// Accent color with alpha
Box(
    modifier = Modifier
        .background(LunarTheme.AccentColor.copy(alpha = LunarTheme.Alpha.Medium))
        .border(LunarTheme.BorderWidth, LunarTheme.BorderColor)
)
```

### Typography

```kotlin
Text(
    text = "Hello World",
    style = LunarTheme.Typography.BodyMedium,
    color = LunarTheme.TextPrimary
)

Text(
    text = "Placeholder",
    style = LunarTheme.Typography.Placeholder
)
```

### Spacing and Radius

```kotlin
Box(
    modifier = Modifier
        .padding(LunarTheme.Spacing.Large)
        .clip(RoundedCornerShape(LunarTheme.CornerRadius.Medium))
)
```

## Components Using the Theme

All components have been updated to use `LunarTheme`:

### TaskExecutorAgentScreen Components
- `TaskExecutorAgentScreen.kt`
- `ModeToggleButton.kt`
- `TextInputPanel.kt`
- `PageHeader.kt`
- `PageFooter.kt`
- `AIStatusIndicator.kt`
- `SphereVisualizer.kt`
- `NeuralNetworkStatus.kt`
- `LoadingIndicator.kt`
- `ActionButton.kt`

### App Drawer Components
- `AppItem.kt`
- `AppDrawerScreen.kt` (uses SearchField which uses theme)

### Home Screen Components
- `HomeScreenContent.kt`
- `SearchField.kt` (core UI component)

### Core UI Components
- `SearchField.kt`

## Migration Guide

When updating existing components to use the theme:

1. **Import the theme:**
   ```kotlin
   import lunar.land.ui.core.theme.LunarTheme
   ```

2. **Replace hardcoded colors:**
   ```kotlin
   // Before
   Color(0xFF4DFF88)
   Color.Black
   Color(0xFF1a1f1a)
   
   // After
   LunarTheme.AccentColor
   LunarTheme.BackgroundColor
   LunarTheme.InactiveBackgroundColor
   ```

3. **Replace hardcoded fonts:**
   ```kotlin
   // Before
   private val manropeFontFamily = FontFamily(...)
   
   // After
   LunarTheme.ManropeFontFamily
   ```

4. **Replace typography:**
   ```kotlin
   // Before
   MaterialTheme.typography.bodyMedium.copy(
       fontFamily = manropeFontFamily,
       fontSize = 14.sp,
       fontWeight = FontWeight.Medium
   )
   
   // After
   LunarTheme.Typography.BodyMedium
   ```

5. **Replace spacing and radius:**
   ```kotlin
   // Before
   .padding(16.dp)
   .clip(RoundedCornerShape(12.dp))
   
   // After
   .padding(LunarTheme.Spacing.Large)
   .clip(RoundedCornerShape(LunarTheme.CornerRadius.Medium))
   ```

## Best Practices

1. **Always use theme constants** - Never hardcode colors, fonts, or spacing values
2. **Use semantic names** - Choose the most appropriate color/text style for the context
3. **Maintain consistency** - Use the same spacing/radius values for similar UI elements
4. **Document exceptions** - If you need a custom value, document why in a comment
5. **Update the theme** - If you need a new color or style, add it to `LunarTheme.kt` rather than creating local constants

## Future Enhancements

Potential additions to the theme system:
- Dark/Light mode variants
- Animation durations and easing curves
- Shadow/elevation values
- Icon sizes
- Component-specific theme variants

