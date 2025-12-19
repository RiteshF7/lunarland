package lunar.land.ui.feature.homescreen

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color as GraphicsColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lunar.land.ui.R
import lunar.land.ui.core.homescreen.model.HomePadding
import lunar.land.ui.core.homescreen.model.LocalHomePadding
import lunar.land.ui.core.model.app.App
import lunar.land.ui.core.model.app.AppWithColor
import lunar.land.ui.core.model.common.State
import lunar.land.ui.core.model.lunarphase.LunarPhase
import lunar.land.ui.core.model.lunarphase.LunarPhaseDetails
import lunar.land.ui.core.model.lunarphase.NextPhaseDetails
import lunar.land.ui.core.model.lunarphase.RiseAndSetDetails
import lunar.land.ui.core.model.lunarphase.UpcomingLunarPhase
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import lunar.land.ui.core.ui.AISphere
import lunar.land.ui.core.ui.SearchField
import lunar.land.ui.core.ui.VerticalSpacer
import lunar.land.ui.core.ui.extensions.isOverlayPermissionGranted
import lunar.land.ui.core.ui.extensions.requestOverlayPermission
import lunar.land.ui.core.ui.extensions.startFloatingVolumeService
import lunar.land.ui.feature.favorites.FavoritesListUiComponent
import lunar.land.ui.feature.favorites.FavoritesListUiComponentState
import lunar.land.ui.feature.lunarHomewidget.LunarHomeWidget
import lunar.land.ui.feature.lunarHomewidget.LunarHomeWidgetState
import lunar.land.ui.core.theme.LunarTheme
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Content component that contains all the main UI elements of the home screen.
 * This component manages all the state and logic for the home screen content.
 */
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    onClockClick: (() -> Unit)? = null,
    onLunarCalendarClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var searchQuery by remember { mutableStateOf("") }
    
    // Create simplified lunar home widget state
    val lunarHomeWidgetState = remember {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(System.currentTimeMillis())
        val clock = kotlinx.datetime.Clock.System
        val now = clock.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        
        val mockLunarPhaseDetails = LunarPhaseDetails(
            lunarPhase = LunarPhase.FULL_MOON,
            illumination = 0.95,
            phaseAngle = 180.0,
            nextPhaseDetails = NextPhaseDetails(
                newMoon = now,
                fullMoon = now
            ),
            moonRiseAndSetDetails = RiseAndSetDetails(
                riseDateTime = now,
                setDateTime = now
            ),
            sunRiseAndSetDetails = RiseAndSetDetails(
                riseDateTime = now,
                setDateTime = now
            )
        )
        
        val mockUpcomingLunarPhase = UpcomingLunarPhase(
            lunarPhase = LunarPhase.WANING_GIBBOUS,
            dateTime = now
        )
        
        LunarHomeWidgetState(
            currentTime = time,
            lunarPhaseDetails = State.Success(mockLunarPhaseDetails),
            upcomingLunarPhase = State.Success(mockUpcomingLunarPhase),
            showIlluminationPercent = true,
            showUpcomingPhaseDetails = true
        )
    }
    
    // Get favorite apps (first 7 apps from launcher) + Driver Activity button
    val favoritesListState = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        
        val favoriteApps = resolveInfos.take(7).mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val app = App(
                name = activityInfo.loadLabel(packageManager).toString(),
                displayName = activityInfo.loadLabel(packageManager).toString(),
                packageName = activityInfo.packageName,
                isSystem = activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            )
            
            // Generate a random but consistent color based on package name
            val random = Random(app.packageName.hashCode())
            val color = GraphicsColor.rgb(
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )
            
            AppWithColor(app = app, color = color)
        }
        
        // Add Driver Activity as a favorite app item
        val driverApp = App(
            name = "Driver",
            displayName = "Driver",
            packageName = context.packageName,
            isSystem = false
        )
        val driverColor = GraphicsColor.rgb(100, 150, 255) // Blue color for driver
        val driverAppWithColor = AppWithColor(app = driverApp, color = driverColor)
        
        FavoritesListUiComponentState(
            favoritesList = persistentListOf<AppWithColor>().addAll(favoriteApps).add(driverAppWithColor),
            eventSink = { /* TODO: Handle events */ }
        )
    }
    
    CompositionLocalProvider(LocalHomePadding provides HomePadding()) {
        val contentPaddingValues = LocalHomePadding.current.contentPaddingValues
        val horizontalPadding = contentPaddingValues.calculateStartPadding(layoutDirection = LayoutDirection.Ltr)
        val topPadding = contentPaddingValues.calculateTopPadding()
        val bottomPadding = contentPaddingValues.calculateBottomPadding()

        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            // Home Screen Content
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section with space and lunar widget
                Column {
                    VerticalSpacer(spacing = topPadding + 40.dp) // Extra space on top
                    
                    LunarHomeWidget(
                        state = lunarHomeWidgetState,
                        horizontalPadding = horizontalPadding,
                        onClick = onLunarCalendarClick ?: onClockClick
                    )
                }
                
                // Bottom section with favorites and search
                Column {
                    FavoritesListUiComponent(
                        state = favoritesListState,
                        contentPadding = bottomPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = bottomPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchField(
                            modifier = Modifier.weight(1f),
                            placeholder = stringResource(id = R.string.search),
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            paddingValues = PaddingValues(horizontal = 0.dp, vertical = 12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Floating Volume Toggle Button
                        IconButton(
                            onClick = {
                                if (context.isOverlayPermissionGranted()) {
                                    context.startFloatingVolumeService()
                                } else {
                                    context.requestOverlayPermission()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume),
                                contentDescription = "Floating Volume",
                                tint = LunarTheme.TextPrimary
                            )
                        }
                    }
                    VerticalSpacer(spacing = bottomPadding)
                }
            }
        }
    }
}

