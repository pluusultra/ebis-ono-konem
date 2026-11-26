import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.LocalDateTime

// ПК-версия на Compose Multiplatform: Windows и Linux.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    // Нативная отрисовка и для Windows, и для Linux: один и тот же jar запускается на обеих системах.
    implementation(compose.desktop.windows_x64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.linux_arm64)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
    // mp3 для будильника: свой звук и встроенный звонок. Java сама играет только wav.
    implementation("javazoom:jlayer:1.0.1")
    // Трей Linux по StatusNotifierItem (D-Bus): KDE, GNOME с AppIndicator и др. slf4j-nop — чтобы библиотека молчала.
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    // Windows: вывести окно из трея на передний план вызовами WinAPI (AttachThreadInput + SetForegroundWindow).
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}

// Скриншот окна и иконки рисуются тестом без показа окна: ./gradlew :desktop:test --tests '*Render*'
tasks.test {
    systemProperty("ebis.projectDir", project.projectDir.absolutePath)
}

// Логотип и иконки — векторы прямо из Android-приложения, чтобы не держать копии.
sourceSets.main {
    resources.srcDir("../app/src/main/res/drawable")
    // Мелодии будильника — те же файлы, что в телефоне.
    resources.srcDir("../app/src/main/res/raw")
}

// Версия растёт сама с каждой сборкой: «1.<месяцев с 2026>.<минута месяца>». Msi с той же upgradeUuid и большей
// версией при установке сам удаляет старую — новый установщик просто ставится поверх. Своя версия: -PappVersion=1.2.3.
val appVersion: String = (project.findProperty("appVersion") as String?)?.removePrefix("v") ?: run {
    val now = LocalDateTime.now()
    val months = (now.year - 2026) * 12 + now.monthValue
    val minute = (now.dayOfMonth - 1) * 1440 + now.hour * 60 + now.minute
    "1.$months.$minute"
}

tasks.processResources {
    inputs.property("appVersion", appVersion)
    filesMatching("ebis-version.properties") { expand("appVersion" to appVersion) }
}

compose.desktop {
    application {
        mainClass = "ru.konem.ebis.desktop.MainKt"
        // Маленькое приложение весь день висит в трее: простой однопоточный сборщик мусора вместо G1 с пачкой
        // фоновых потоков, и свободную память куча отдаёт системе, а не держит про запас.
        jvmArgs += listOf("-XX:+UseSerialGC", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30")
        nativeDistributions {
            // Установщики собираются только на своей системе: Msi на Windows, Deb/Rpm на Linux.
            // Msi включаем по -Pinstaller: иначе плагин на Windows тянет WiX даже ради простой папки с exe.
            if (project.hasProperty("installer")) targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            else targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "EbisOnoKonem"
            packageVersion = appVersion
            // Латиницей: WiX собирает msi в кодировке 1252 и падает на кириллице (LGHT0311).
            description = "Ebis ono konem - task tracker"
            vendor = "konem"
            modules("java.prefs", "jdk.security.auth")
            windows {
                menuGroup = "Ebis"
                shortcut = true
                // Без выбора папки: обновление ставится туда же и не спрашивает «папка уже есть».
                dirChooser = false
                // В свою папку пользователя: без прав администратора, обновления ставятся туда же.
                perUserInstall = true
                upgradeUuid = "6f6b6f6e-656d-4562-6973-6f6e6f6b6f6e"
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                shortcut = true
                packageName = "ebis-ono-konem"
                appCategory = "Office"
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
