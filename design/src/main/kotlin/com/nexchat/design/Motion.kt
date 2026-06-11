package com.nexchat.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

val SpringMedium = spring<Float>(stiffness = Spring.StiffnessMedium, dampingRatio = 0.8f)
val SpringSnappy = spring<Float>(stiffness = Spring.StiffnessHigh, dampingRatio = 0.75f)
val EasingStandard = FastOutSlowInEasing

const val MessageDuration = 150
const val MediaReveal     = 300
const val InputBarSlide   = 200
const val ReactionFloat   = 400
