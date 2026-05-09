package fi.kotinaytto.tv.dream

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import fi.kotinaytto.tv.ui.DreamScreensaverContent
import fi.kotinaytto.tv.ui.KotiTheme

class KotiDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    KotiTheme {
                        DreamScreensaverContent()
                    }
                }
            },
        )
    }
}
