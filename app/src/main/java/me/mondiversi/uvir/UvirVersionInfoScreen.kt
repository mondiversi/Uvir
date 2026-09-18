package me.mondiversi.uvir

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirVersionInfoScreen(
    scrollState: ScrollState,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val githubRepositoryUrl =
        BuildConfig.GITHUB_REPOSITORY_URL
            .trim()
            .trimEnd('/')

    UvirFullScreenPage(
        onDismissRequest = onDismissRequest,
        title = {
            UvirMenuTitle(
                text =
                    stringResource(
                        R.string.version_info_title,
                        BuildConfig.VERSION_NAME
                    )
            )
        },
        containerColor = backgroundColor,
        contentColor = primaryText,
        scrollState = scrollState,
        scrollbarColor = secondaryText.copy(alpha = 0.46f),
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                verticalArrangement =
                    Arrangement.spacedBy(UvirIslandSpacing)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier =
                            Modifier.padding(UvirIslandContentPadding),
                        verticalArrangement =
                            Arrangement.spacedBy(UvirSettingsControlGap)
                    ) {
                        Text(
                            text = stringResource(R.string.about_description),
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.about_derived_values),
                            color = secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.about_whats_new_title,
                            BuildConfig.VERSION_NAME
                        ),
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor = secondaryText.copy(alpha = 0.28f),
                    titleIconContent = { tint ->
                        WhatsNewIcon(tint = tint)
                    }
                ) {
                    listOf(
                        stringResource(R.string.about_whats_new_connectivity),
                        stringResource(R.string.about_whats_new_connections),
                        stringResource(R.string.about_whats_new_measurements),
                        stringResource(R.string.about_whats_new_interface),
                        stringResource(R.string.about_whats_new_github)
                    ).forEach { change ->
                        Text(
                            text = "• $change",
                            color = secondaryText,
                            fontSize = 13.sp
                        )
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.github_repository_title),
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor = secondaryText.copy(alpha = 0.28f),
                    titleIconContent = { tint ->
                        GitHubIcon(tint = tint)
                    }
                ) {
                    Text(
                        text =
                            if (githubRepositoryUrl.isBlank()) {
                                stringResource(
                                    R.string.github_repository_pending
                                )
                            } else {
                                githubRepositoryUrl
                            },
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(githubRepositoryUrl)
                                    )
                                )
                            },
                            enabled = githubRepositoryUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = uvirOutlinedActionColors(primaryText),
                            border =
                                uvirOutlinedActionBorder(
                                    githubRepositoryUrl.isNotBlank(),
                                    secondaryText
                                )
                        ) {
                            UvirLabeledButtonContent(
                                text = stringResource(R.string.open_github_repository)
                            ) {
                                GitHubIcon(
                                    modifier = Modifier.size(20.dp),
                                    tint = LocalContentColor.current
                                )
                            }
                        }

                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("$githubRepositoryUrl/releases")
                                    )
                                )
                            },
                            enabled = githubRepositoryUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = uvirPrimaryButtonColors()
                        ) {
                            UvirLabeledButtonContent(
                                text = stringResource(R.string.check_for_updates)
                            ) {
                                UvirButtonGlyphIcon(UvirButtonGlyph.REFRESH)
                            }
                        }
                    }
                }

                UvirProjectNumbersSection(
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier =
                            Modifier.padding(UvirIslandContentPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.biological_model_version,
                                    BIOLOGICAL_MODEL_VERSION
                                ),
                            fontSize = 12.sp,
                            fontWeight =
                                androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.about_copyright),
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                        Text(
                            text = stringResource(R.string.about_license),
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.package_name_value,
                                    context.packageName
                                ),
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    )
}
