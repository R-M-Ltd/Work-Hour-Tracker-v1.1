package com.rmltd.workhourstracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.R
import kotlinx.coroutines.launch

/**
 * Locked first-run onboarding copy (1.3.37 DESIGN-SPEC).
 * Exposed for JVM unit tests — do not invent cloud / OAuth copy here.
 */
object OnboardingCopy {
    const val PAGE_COUNT = 3

    const val WELCOME_TITLE = "Track your work hours"
    const val WELCOME_BODY =
        "Clock in and out, log today’s hours, and keep a clear history — all in one place."

    const val WALKTHROUGH_TITLE = "Clock in. Log hours. Review."
    const val FEAT_CLOCK_TITLE = "Clock in & out"
    const val FEAT_CLOCK_BODY = "One tap on Home starts or ends your day."
    const val FEAT_HOURS_TITLE = "Today’s hours"
    const val FEAT_HOURS_BODY =
        "Add or change a typed total when punches aren’t enough."
    const val FEAT_HISTORY_TITLE = "History"
    const val FEAT_HISTORY_BODY =
        "Past days stay in ⋮ → History when you need them."

    const val READY_TITLE = "You’re ready"
    const val READY_BODY =
        "Head to Home — clock the day or add hours whenever you like."

    const val SKIP = "Skip"
    const val NEXT = "Next"
    const val GET_STARTED = "Get started"

    const val CD_SKIP = "Skip onboarding"
    const val CD_NEXT = "Next page"
    const val CD_GET_STARTED = "Get started and open Home"
    const val CD_MARK = "Work Hours Tracker"
}

/**
 * Three-page HorizontalPager onboarding (Welcome → Walkthrough → Get started).
 * Skip (every page) and Get started both invoke [onComplete] — caller sets prefs
 * and navigates Home with popUpTo(onboarding) inclusive.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingCopy.PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val surfaceFamily = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaceFamily
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { contentDescription = OnboardingCopy.CD_SKIP }
                ) {
                    Text(OnboardingCopy.SKIP)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> WalkthroughPage()
                    else -> ReadyPage()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageDots(
                    pageCount = OnboardingCopy.PAGE_COUNT,
                    currentPage = pagerState.currentPage
                )
                Spacer(Modifier.height(20.dp))
                val isLast = pagerState.currentPage >= OnboardingCopy.PAGE_COUNT - 1
                Button(
                    onClick = {
                        if (isLast) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .height(52.dp)
                        .semantics {
                            contentDescription =
                                if (isLast) OnboardingCopy.CD_GET_STARTED
                                else OnboardingCopy.CD_NEXT
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isLast) OnboardingCopy.GET_STARTED else OnboardingCopy.NEXT,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ArcClockMark()
        Spacer(Modifier.height(28.dp))
        Text(
            text = OnboardingCopy.WELCOME_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = OnboardingCopy.WELCOME_BODY,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WalkthroughPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = OnboardingCopy.WALKTHROUGH_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(20.dp))
        FeatureRow(
            icon = Icons.Filled.Schedule,
            title = OnboardingCopy.FEAT_CLOCK_TITLE,
            body = OnboardingCopy.FEAT_CLOCK_BODY
        )
        Spacer(Modifier.height(12.dp))
        FeatureRow(
            icon = Icons.Filled.Edit,
            title = OnboardingCopy.FEAT_HOURS_TITLE,
            body = OnboardingCopy.FEAT_HOURS_BODY
        )
        Spacer(Modifier.height(12.dp))
        FeatureRow(
            icon = Icons.Filled.History,
            title = OnboardingCopy.FEAT_HISTORY_TITLE,
            body = OnboardingCopy.FEAT_HISTORY_BODY
        )
    }
}

@Composable
private fun ReadyPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = OnboardingCopy.READY_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = OnboardingCopy.READY_BODY,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Arc Clock mark — reuses existing launcher/splash drawable on brand purple squircle.
 * Does not rewrite icon packs.
 */
@Composable
private fun ArcClockMark() {
    val brandPurple = Color(0xFF5B3F9E)
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(brandPurple)
            .semantics { contentDescription = OnboardingCopy.CD_MARK },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_icon),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "Page ${currentPage + 1} of $pageCount"
        }
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(
                        width = if (active) 20.dp else 8.dp,
                        height = 8.dp
                    )
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
