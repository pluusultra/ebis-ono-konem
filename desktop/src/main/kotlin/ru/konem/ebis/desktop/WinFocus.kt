package ru.konem.ebis.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Окно из трея на передний план в Windows. Windows отдаёт фокус только процессу, который сейчас на переднем
 * плане, а после клика по трею это панель задач — toFront() окно поднимает, но активным не делает. Обходной путь
 * из документации к SetForegroundWindow: на время подключиться к очереди ввода окна, которое сейчас активно.
 * Без этого приходилось сворачивать и разворачивать окно, и Windows играла анимацию — текст «скакал».
 */
object WinFocus {
    /** Окно на переднем плане по мнению самой Windows. Java ([Window.isActive]) узнаёт об этом с опозданием. */
    fun isForeground(window: Window): Boolean = runCatching {
        User32.INSTANCE.GetForegroundWindow() == WinDef.HWND(Native.getComponentPointer(window))
    }.getOrDefault(false)

    /** true — окно стало активным. */
    fun bringToFront(window: Window): Boolean = runCatching {
        val u = User32.INSTANCE
        val hwnd = WinDef.HWND(Native.getComponentPointer(window))
        val fg = u.GetForegroundWindow()
        if (fg == hwnd) {
            WinDebug.log("WinAPI: окно уже на переднем плане (Java: активно=${window.isActive})")
            return true
        }
        val me = Kernel32.INSTANCE.GetCurrentThreadId()
        val fgThread = if (fg != null) u.GetWindowThreadProcessId(fg, null) else 0
        val winThread = u.GetWindowThreadProcessId(hwnd, null)
        val attachMe = fgThread != 0 && fgThread != me && u.AttachThreadInput(WinDef.DWORD(me.toLong()), WinDef.DWORD(fgThread.toLong()), true)
        // Само окно живёт в потоке AWT-Windows, а не в нашем — подключаем к активной очереди и его.
        val attachWin = fgThread != 0 && winThread != fgThread && winThread != me &&
            u.AttachThreadInput(WinDef.DWORD(winThread.toLong()), WinDef.DWORD(fgThread.toLong()), true)
        try {
            u.ShowWindow(hwnd, User32.SW_SHOW)
            u.BringWindowToTop(hwnd)
            u.SetForegroundWindow(hwnd)
        } finally {
            if (attachWin) u.AttachThreadInput(WinDef.DWORD(winThread.toLong()), WinDef.DWORD(fgThread.toLong()), false)
            if (attachMe) u.AttachThreadInput(WinDef.DWORD(me.toLong()), WinDef.DWORD(fgThread.toLong()), false)
        }
        val ok = u.GetForegroundWindow() == hwnd
        WinDebug.log("WinAPI: передний план → ${if (ok) "наше окно" else "не дали"} (подключение к вводу: моё=$attachMe, окна=$attachWin)")
        ok
    }.getOrElse {
        Log.w("win", "Не вышло вывести окно на передний план через WinAPI", it)
        false
    }
}
