# Glance находит экземпляры виджета по его классу (GlanceAppWidgetManager.getGlanceIds).
# R8 склеивал похожие классы виджетов в один, и updateAll() счётчика перерисовывал
# серию, «Горит» и «Накинуть» как счётчик. Классы виджетов не трогаем.
-keep class ru.konem.ebis.widget.** extends androidx.glance.appwidget.GlanceAppWidget
