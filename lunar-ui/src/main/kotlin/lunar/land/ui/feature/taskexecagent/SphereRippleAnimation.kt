package lunar.land.ui.feature.taskexecagent

import kotlin.math.abs

/**
 * Pure Kotlin animation logic for the sphere ripple effect.
 * This handles the wave animation calculations without Compose dependencies.
 */

/**
 * Configuration for the ripple animation
 */
data class RippleConfig(
    val durationMs: Int = 1200,
    val maxAlpha: Float = 0.8f,
    val minAlpha: Float = 0.02f,
    val waveWidth: Float = 0.15f // Width of the wave as fraction of total radius
)

/**
 * Calculates the alpha multiplier for a circle layer based on ripple progress.
 * 
 * @param progress The current animation progress (0.0 to 1.0)
 * @param layerPosition The normalized position of the layer (0.0 = center, 1.0 = outer edge)
 * @param config The ripple configuration
 * @return Alpha multiplier (0.0 to 1.0) to apply to the layer's base alpha
 */
fun calculateRippleAlpha(
    progress: Float,
    layerPosition: Float,
    config: RippleConfig = RippleConfig()
): Float {
    if (progress <= 0f) return 1f
    
    // Create a wave that travels from center (0.0) to edge (1.0) and back
    // First half: wave travels outward (0.0 -> 1.0)
    // Second half: wave travels inward (1.0 -> 0.0)
    val wavePosition = if (progress < 0.5f) {
        // Outward: progress 0.0 -> 0.5 maps to wave position 0.0 -> 1.0
        progress * 2f
    } else {
        // Inward: progress 0.5 -> 1.0 maps to wave position 1.0 -> 0.0
        2f - (progress * 2f)
    }
    
    // Calculate distance from wave center to layer position
    val distance = abs(layerPosition - wavePosition)
    
    // Apply wave effect: layers near the wave get brighter
    val waveEffect = when {
        distance < config.waveWidth -> {
            // Inside wave: calculate intensity based on distance from wave center
            val normalizedDistance = distance / config.waveWidth
            // Use smooth falloff (ease-out curve)
            val intensity = 1f - (normalizedDistance * normalizedDistance)
            intensity
        }
        else -> {
            // Outside wave: no effect
            0f
        }
    }
    
    // Apply the wave effect to alpha
    // Base alpha is 1.0, wave adds extra brightness
    val alphaMultiplier = 1f + (waveEffect * (config.maxAlpha - 1f))
    
    return alphaMultiplier.coerceIn(0f, config.maxAlpha)
}

/**
 * Calculates the stroke width multiplier for a circle layer based on ripple progress.
 * Creates a subtle pulsing effect on the wave.
 * 
 * @param progress The current animation progress (0.0 to 1.0)
 * @param layerPosition The normalized position of the layer (0.0 = center, 1.0 = outer edge)
 * @param config The ripple configuration
 * @return Stroke width multiplier (1.0 to maxWidthMultiplier)
 */
fun calculateRippleStrokeWidth(
    progress: Float,
    layerPosition: Float,
    config: RippleConfig = RippleConfig(),
    maxWidthMultiplier: Float = 1.5f
): Float {
    if (progress <= 0f) return 1f
    
    val wavePosition = if (progress < 0.5f) {
        progress * 2f
    } else {
        2f - (progress * 2f)
    }
    
    val distance = abs(layerPosition - wavePosition)
    
    val waveEffect = when {
        distance < config.waveWidth -> {
            val normalizedDistance = distance / config.waveWidth
            val intensity = 1f - (normalizedDistance * normalizedDistance)
            intensity
        }
        else -> 0f
    }
    
    // Stroke width increases slightly in the wave
    return 1f + (waveEffect * (maxWidthMultiplier - 1f))
}

/**
 * Normalizes a layer index to a position value (0.0 to 1.0).
 * 
 * @param layerIndex The index of the layer (0-based)
 * @param totalLayers Total number of layers
 * @param startRadius Fraction of base radius where layers start (e.g., 0.1f for 10%)
 * @param endRadius Fraction of base radius where layers end (e.g., 1.0f for 100%)
 * @return Normalized position (0.0 = inner, 1.0 = outer)
 */
fun normalizeLayerPosition(
    layerIndex: Int,
    totalLayers: Int,
    startRadius: Float = 0.1f,
    endRadius: Float = 1.0f
): Float {
    if (totalLayers <= 1) return 0.5f
    
    val normalizedIndex = layerIndex.toFloat() / (totalLayers - 1).coerceAtLeast(1)
    // Map from layer index to actual radius position
    val radiusPosition = startRadius + (normalizedIndex * (endRadius - startRadius))
    // Normalize to 0.0-1.0 range
    return (radiusPosition - startRadius) / (endRadius - startRadius).coerceAtLeast(0.001f)
}

/**
 * Calculates ripple effect for extended circles beyond the base radius.
 * Used for full-screen ripple effects.
 * 
 * @param progress The current animation progress (0.0 to 1.0)
 * @param radius The actual radius of the circle
 * @param baseRadius The base radius of the sphere (where ripple starts)
 * @param maxRadius The maximum radius to extend to (screen diagonal)
 * @param config The ripple configuration
 * @return Pair of (alpha, strokeWidthMultiplier) for the ripple circle
 */
fun calculateExtendedRipple(
    progress: Float,
    radius: Float,
    baseRadius: Float,
    maxRadius: Float,
    config: RippleConfig = RippleConfig()
): Pair<Float, Float> {
    if (progress <= 0f || radius < baseRadius) return Pair(0f, 1f)
    
    // Normalize position: 0.0 = at baseRadius, 1.0 = at maxRadius
    val normalizedPosition = (radius - baseRadius) / (maxRadius - baseRadius).coerceAtLeast(0.001f)
    
    // Wave travels from 0.0 (baseRadius) to 2.0 (maxRadius and beyond)
    // First half: 0.0 -> 2.0 (outward)
    // Second half: 2.0 -> 0.0 (inward)
    val wavePosition = if (progress < 0.5f) {
        progress * 4f // 0.0 -> 2.0
    } else {
        4f - (progress * 4f) // 2.0 -> 0.0
    }
    
    // Map normalized position to same scale (0.0 to 2.0)
    val ripplePosition = normalizedPosition * 2f
    
    val distance = abs(ripplePosition - wavePosition)
    val waveWidth = 0.25f
    
    val waveEffect = when {
        distance < waveWidth -> {
            val normalizedDistance = distance / waveWidth
            val intensity = 1f - (normalizedDistance * normalizedDistance)
            intensity
        }
        else -> 0f
    }
    
    // Alpha fades as distance from center increases
    val distanceAlpha = (1f - (normalizedPosition * 0.6f)).coerceAtLeast(0.15f)
    val alpha = waveEffect * 0.7f * distanceAlpha
    
    // Stroke width increases with wave effect
    val strokeWidthMultiplier = 1f + (waveEffect * 0.8f)
    
    return Pair(alpha, strokeWidthMultiplier)
}

