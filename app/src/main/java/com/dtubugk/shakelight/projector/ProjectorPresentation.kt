package com.dtubugk.shakelight.projector

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager

/** Full-screen content on a second screen (projector/TV over HDMI, USB-C or Miracast). */
class ProjectorPresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {

    val stage = MediaStage(context)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(stage)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        stage.release()
        super.onStop()
    }
}
