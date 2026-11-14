package com.sevtinge.hyperceiler.libhook.rules.systemui.controlcenter.media2

import android.view.View
import android.view.ViewGroup
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.controlcenter.PublicClass.mediaViewHolder
import com.sevtinge.hyperceiler.libhook.utils.hookapi.systemui.controlcenter.PublicClass.mediaViewHolderNew
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.java.Constructors
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectFieldOrNullAs

object DisableMediaCard : BaseHook() {

    override fun init() {
        if (!PrefsBridge.getBoolean("system_ui_control_center_media_control_disable_card")) {
            return
        }

        // 1. Data layer: suppress active media reports so SystemUI does not reserve space or show media
        val mediaDataManagerClasses = listOf(
            "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl",
            "com.android.systemui.media.controls.domain.pipeline.MediaDataManager",
            "com.android.systemui.media.controls.pipeline.MediaDataManager",
            "com.android.systemui.media.MediaDataManager"
        )

        for (clsName in mediaDataManagerClasses) {
            val cls = findClassIfExists(clsName) ?: continue
            cls.findAllMethods {
                name("hasActiveMedia", "hasAnyMedia", "hasActiveMediaOrRecommendation", "hasAnyMediaOrRecommendation")
            }.forEach { method ->
                method.createHook {
                    returnConstant(false)
                }
            }
        }

        // 2. MediaHost layer: force visibility state to false and collapse hostView
        val mediaHostClasses = listOf(
            "com.android.systemui.media.controls.ui.view.MediaHost",
            "com.android.systemui.media.controls.ui.MediaHost",
            "com.android.systemui.media.MediaHost"
        )

        for (clsName in mediaHostClasses) {
            val cls = findClassIfExists(clsName) ?: continue
            cls.findAllMethods {
                name("getVisible", "getShowsCount")
            }.forEach { method ->
                method.createHook {
                    returnConstant(false)
                }
            }

            try {
                cls.findMethod {
                    name("updateViewVisibility")
                }.createAfterHook {
                    val hostView = it.thisObject.getObjectFieldOrNullAs<View>("hostView")
                    if (hostView != null) {
                        hostView.visibility = View.GONE
                        val lp = hostView.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            hostView.layoutParams = lp
                        }
                    }
                }
            } catch (t: Throwable) {
                // Ignore if method not found
            }
        }

        // 3. Carousel layer: suppress addOrUpdatePlayer and collapse carousel/frames
        val carouselClasses = listOf(
            "com.android.systemui.media.controls.ui.controller.MediaCarouselController",
            "com.android.systemui.media.controls.ui.MediaCarouselController",
            "com.android.systemui.media.MediaCarouselController"
        )

        for (clsName in carouselClasses) {
            val cls = findClassIfExists(clsName) ?: continue
            try {
                cls.findMethod {
                    name("addOrUpdatePlayer")
                }.createHook {
                    returnConstant(false)
                }
            } catch (t: Throwable) {
                // Ignore
            }

            try {
                cls.findMethod {
                    name("onDesiredLocationChanged")
                }.createAfterHook {
                    val target = it.thisObject
                    val mediaFrame = target.getObjectFieldOrNullAs<View>("mediaFrame")
                    val mediaCarousel = target.getObjectFieldOrNullAs<View>("mediaCarousel")
                    listOfNotNull(mediaFrame, mediaCarousel).forEach { v ->
                        v.visibility = View.GONE
                        val lp = v.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            v.layoutParams = lp
                        }
                    }
                }
            } catch (t: Throwable) {
                // Ignore
            }
        }

        // 4. View holder fallback: ensure individual player views stay collapsed
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
                // Ignore
            }
        }
    }
}
