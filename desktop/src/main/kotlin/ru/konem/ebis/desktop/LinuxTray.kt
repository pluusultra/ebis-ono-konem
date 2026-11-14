@file:Suppress("FunctionName", "LocalVariableName")

package ru.konem.ebis.desktop

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * Значок в трее Linux по протоколу StatusNotifierItem (D-Bus): так его рисуют KDE, GNOME с AppIndicator, XFCE, Cinnamon.
 * Обычный трей Java (XEmbed) в KDE получает непрозрачный белый фон вокруг значка, а в GNOME под Wayland его нет вовсе.
 * Нет StatusNotifierWatcher (оболочка без такого трея) — [start] вернёт false, и останется трей Java.
 */
object LinuxTray {
    /** Пункт меню; null в списке — разделитель. */
    class Item(val label: String, val action: () -> Unit)

    private const val ITEM_PATH = "/StatusNotifierItem"
    private const val MENU_PATH = "/MenuBar"

    @Volatile var active = false
        private set
    private var conn: DBusConnection? = null

    fun start(title: String, items: List<Item?>, onActivate: () -> Unit): Boolean {
        if (Launcher.isWindows) return false
        return runCatching {
            val c = DBusConnectionBuilder.forSessionBus().withShared(false).build()
            conn = c
            val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            c.requestBusName(name)
            c.exportObject(ITEM_PATH, SniItem(title, pixmaps(), onActivate))
            c.exportObject(MENU_PATH, SniMenu(items))
            c.getRemoteObject("org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher", StatusNotifierWatcher::class.java)
                .RegisterStatusNotifierItem(name)
            active = true
            Log.i("tray", "Трей через StatusNotifierItem (D-Bus)")
            true
        }.getOrElse {
            Log.w("tray", "StatusNotifierItem недоступен, будет обычный трей: $it${it.cause?.let { c -> ", причина: $c" } ?: ""}")
            stop()
            false
        }
    }

    fun stop() {
        active = false
        runCatching { conn?.disconnect() }
        conn = null
    }

    /** Значок в нескольких размерах, ARGB по байтам в сетевом порядке — как требует протокол. */
    private fun pixmaps(): List<Pixmap> {
        val src = LinuxTray::class.java.getResourceAsStream("/notif/logo.png")?.use { ImageIO.read(it) } ?: return emptyList()
        return listOf(16, 22, 24, 32, 48, 64).map { s ->
            val img = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(src, 0, 0, s, s, null)
                dispose()
            }
            val bytes = ByteArray(s * s * 4)
            var i = 0
            for (y in 0 until s) for (x in 0 until s) {
                val p = img.getRGB(x, y)
                bytes[i++] = (p ushr 24).toByte()
                bytes[i++] = (p ushr 16).toByte()
                bytes[i++] = (p ushr 8).toByte()
                bytes[i++] = p.toByte()
            }
            Pixmap(s, s, bytes)
        }
    }

    private fun ui(block: () -> Unit) = SwingUtilities.invokeLater { runCatching(block).onFailure { Log.e("tray", "Пункт трея упал", it) } }

    // ── Объекты на шине ──

    private class SniItem(private val title: String, private val icons: List<Pixmap>, private val onActivate: () -> Unit) :
        StatusNotifierItem, Properties {
        override fun getObjectPath() = ITEM_PATH
        override fun Activate(x: Int, y: Int) = ui(onActivate)
        override fun SecondaryActivate(x: Int, y: Int) = ui(onActivate)
        override fun ContextMenu(x: Int, y: Int) {}
        override fun Scroll(delta: Int, orientation: String) {}

        private fun props(): Map<String, Variant<*>> = mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("ebis-ono-konem"),
            "Title" to Variant(title),
            "Status" to Variant("Active"),
            "IconName" to Variant(""),
            "IconPixmap" to Variant(icons, "a(iiay)"),
            "ToolTip" to Variant(ToolTip("", icons, title, ""), "(sa(iiay)ss)"),
            "ItemIsMenu" to Variant(false),
            "Menu" to Variant(DBusPath(MENU_PATH), "o"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (props()[propertyName] ?: throw IllegalArgumentException("No property $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {}
        override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
            if (interfaceName == "org.kde.StatusNotifierItem") props() else emptyMap()
    }

    /** Меню по протоколу com.canonical.dbusmenu: корень 0, пункты 1..n. */
    private class SniMenu(private val items: List<Item?>) : DbusMenu, Properties {
        override fun getObjectPath() = MENU_PATH

        private fun itemProps(id: Int): Map<String, Variant<*>> {
            if (id == 0) return mapOf("children-display" to Variant("submenu"))
            val it = items.getOrNull(id - 1)
            return if (it == null) mapOf("type" to Variant("separator"), "visible" to Variant(true))
            else mapOf("label" to Variant(it.label), "enabled" to Variant(true), "visible" to Variant(true))
        }

        private fun node(id: Int): MenuNode {
            val children = if (id == 0) items.indices.map { Variant(node(it + 1), "(ia{sv}av)") } else emptyList()
            return MenuNode(id, itemProps(id), children)
        }

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>) = Tuple2(UInt32(1), node(parentId))
        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<IdProps> =
            (if (ids.isEmpty()) (0..items.size).toList() else ids).map { IdProps(it, itemProps(it)) }
        override fun GetProperty(id: Int, name: String): Variant<*> = itemProps(id)[name] ?: Variant("")
        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId == "clicked") items.getOrNull(id - 1)?.let { ui(it.action) }
        }
        override fun EventGroup(events: List<MenuEvent>): List<Int> {
            events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
            return emptyList()
        }
        override fun AboutToShow(id: Int) = false
        override fun AboutToShowGroup(ids: List<Int>) = Tuple2<List<Int>, List<Int>>(emptyList(), emptyList())

        private fun props(): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (props()[propertyName] ?: throw IllegalArgumentException("No property $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {}
        override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
            if (interfaceName == "com.canonical.dbusmenu") props() else emptyMap()
    }
}

// ── Интерфейсы и типы D-Bus (имена методов — как в протоколе) ──

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)
}

@JvmSuppressWildcards
@DBusInterfaceName("com.canonical.dbusmenu")
interface DbusMenu : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): Tuple2<UInt32, MenuNode>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<IdProps>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<MenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: List<Int>): Tuple2<List<Int>, List<Int>>
}

class Pixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

@JvmSuppressWildcards
class ToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val pixmaps: List<Pixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val text: String,
) : Struct()

@JvmSuppressWildcards
class MenuNode(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val props: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

@JvmSuppressWildcards
class IdProps(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val props: Map<String, Variant<*>>,
) : Struct()

class MenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()

/** Несколько выходных значений метода. dbus-java берёт их типы из параметров класса, поэтому он обобщённый. */
class Tuple2<A, B>(
    @field:Position(0) @JvmField val a: A,
    @field:Position(1) @JvmField val b: B,
) : Tuple()
