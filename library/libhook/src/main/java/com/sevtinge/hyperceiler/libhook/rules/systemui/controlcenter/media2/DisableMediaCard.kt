package com.sevtinge.hyperceiler.libhook.rules.systemui.controlcenter.media2

import android.view.View
import android.view.ViewGroup
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.controlcenter.PublicClass.mediaViewHolder
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.controlcenter.PublicClass.mediaViewHolderNew
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.java.Constructors
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectFieldOrNullAs

object DisableMediaCard : BaseHook() {

    override fun init() {
        if (!PrefsBridge.getBoolean("system_ui_control_center_media_control_disable_card")) {
            return
        }

        val holders = listOfNotNull(mediaViewHolder, mediaViewHolderNew)
        for (holderClass in holders) {
            try {
                Constructors.find(holderClass).first().createAfterHook {
                    val holder = it.thisObject
                    val playerView = holder.getObjectFieldOrNullAs<View>("player")
                    if (playerView != null) {
                        playerView.visibility = View.GONE
                        val lp = playerView.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            lp.width = 0
                            if (lp is ViewGroup.MarginLayoutParams) {
                                lp.topMargin = 0
                                lp.bottomMargin = 0
                            }
                            playerView.layoutParams = lp
                        }
                    }
                }
            } catch (t: Throwable) {
                // Ignore if specific constructor is not found
            }
        }

        try {
            val carouselController = findClassIfExists("com.android.systemui.media.controls.ui.controller.MediaCarouselController")
                ?: findClassIfExists("com.android.systemui.media.controls.ui.MediaCarouselController")
            if (carouselController != null) {
                carouselController.findMethod {
                    filterByName("onDesiredLocationChanged")
                }.firstOrNull()?.createAfterHook {
                    val mediaFrame = it.thisObject.getObjectFieldOrNullAs<View>("mediaFrame")
                    if (mediaFrame != null) {
                        mediaFrame.visibility = View.GONE
                        val lp = mediaFrame.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            lp.width = 0
                            mediaFrame.layoutParams = lp
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
