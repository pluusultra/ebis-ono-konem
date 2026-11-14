package ru.konem.ebis.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Общий ритм движения. Всё в приложении двигается на этих пружинах,
 * чтобы экраны, карточки и попапы ощущались одним целым.
 */
object Motion {
    /** Кнопки, галочки, мелкие отклики: быстро и с лёгким перелётом. */
    fun <T> snappy(threshold: T? = null) = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium, visibilityThreshold = threshold)

    /** Попапы и «выпрыгивания»: заметный отскок. */
    fun <T> bouncy(threshold: T? = null) = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = threshold)

    /** Экраны и шторки: плавно, без отскока. */
    fun <T> smooth(threshold: T? = null) = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = threshold)

    /** Перестановки в списках. */
    val placement: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))

    val screenOffset: FiniteAnimationSpec<IntOffset> = smooth(IntOffset(1, 1))

    fun <T> fade(ms: Int = 220) = tween<T>(ms)
}
