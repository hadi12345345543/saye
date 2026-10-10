package ir.saya.lan

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import java.net.ServerSocket
import java.net.Socket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val peers = CopyOnWriteArrayList<Peer>()
    private val messages = CopyOnWriteArrayList<String>()
    private lateinit var root: LinearLayout
    private lateinit var log: LinearLayout
    private lateinit var input: EditText
    private lateinit var ipInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var status: TextView
    private var dark = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var connected = false

    data class Peer(val socket: Socket, val reader: BufferedReader, val writer: PrintWriter)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 10)
            setBackgroundColor(if (dark) Color.rgb(20,25,34) else Color.rgb(244,247,251))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "SAYA  •  سایه"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (dark) Color.WHITE else Color.rgb(27,48,75))
        }
        val theme = Button(this).apply { text = "◐"; setOnClickListener { dark = !dark; buildUi() } }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(theme)

        status = TextView(this).apply {
            text = "برای شروع، روی یک گوشی «میزبان» و روی گوشی دیگر IP و رمز را وارد کن."
            textSize = 13f; setPadding(0, 6, 0, 8)
            setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
        }
        ipInput = EditText(this).apply {
            hint = "IP میزبان، مثل 192.168.1.20"
            singleLine = true
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
        }
        passwordInput = EditText(this).apply {
            hint = "رمز اتاق"
            singleLine = true
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "میزبان شو"
            setOnClickListener { startHost() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(Button(this).apply {
            text = "اتصال به IP"
            setOnClickListener { connectToHost() }
        }, LinearLayout.LayoutParams(0, -2, 1f))

        log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 8, 4, 8) }
        val scroll = ScrollView(this).apply { addView(log) }
        input = EditText(this).apply {
            hint = "پیامت را بنویس…"; textSize = 16f; singleLine = true
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
        }
        val send = Button(this).apply { text = "ارسال"; setOnClickListener { sendMessage() } }
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); composer.addView(send)

        root.addView(header)
        root.addView(status)
        root.addView(ipInput)
        root.addView(passwordInput)
        root.addView(buttons)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(composer)
        setContentView(root)
        messages.forEach { addBubble(it) }
        if (!connected) status.text = "IP این گوشی: ${localIp()}  •  هر دو گوشی روی یک Wi‑Fi باشند"
    }

    private fun startHost() {
        val password = passwordInput.text.toString()
        if (password.isBlank()) { toast("اول یک رمز وارد کن."); return }
        if (server != null) { toast("میزبانی از قبل فعال است."); return }
        thread {
            try {
                server = ServerSocket(45454)
                runOnUiThread { status.text = "میزبان فعال است • IP: ${localIp()} • پورت 45454" }
                while (server?.isClosed == false) {
                    val socket = server!!.accept()
                    thread { acceptPeer(socket, password) }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطا در میزبانی: ${e.message}" }
            }
        }
    }

    private fun acceptPeer(socket: Socket, expectedPassword: String) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val supplied = reader.readLine() ?: ""
            if (supplied != expectedPassword) {
                writer.println("AUTH_FAIL")
                socket.close()
                return
            }
            writer.println("AUTH_OK")
            val peer = Peer(socket, reader, writer)
            peers.add(peer)
            runOnUiThread { status.text = "یک گوشی متصل شد • IP: ${localIp()}" }
            while (true) {
                val line = reader.readLine() ?: break
                runOnUiThread { showIncoming(line) }
                peers.filter { it.socket !== socket }.forEach { runCatching { it.writer.println(line) } }
            }
        } catch (_: Exception) {
        } finally {
            peers.removeAll { it.socket === socket }
            runCatching { socket.close() }
        }
    }

    private fun connectToHost() {
        val host = ipInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (host.isBlank() || password.isBlank()) { toast("IP و رمز را وارد کن."); return }
        thread {
            try {
                val socket = Socket(host, 45454)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(password)
                val result = reader.readLine()
                if (result != "AUTH_OK") {
                    socket.close()
                    runOnUiThread { toast("رمز اشتباه است یا اتصال رد شد.") }
                    return@thread
                }
                val peer = Peer(socket, reader, writer)
                peers.add(peer)
                connected = true
                runOnUiThread { status.text = "متصل به $host • چت آماده است" }
                while (true) {
                    val line = reader.readLine() ?: break
                    runOnUiThread { showIncoming(line) }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("اتصال ناموفق: ${e.message}") }
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (peers.isEmpty()) { toast("هنوز کسی متصل نیست."); return }
        val line = "من: $text"
        messages.add(line)
        addBubble(line)
        peers.forEach { runCatching { it.writer.println(line) } }
        input.setText("")
    }

    private fun showIncoming(line: String) {
        messages.add(line)
        addBubble(line)
    }

    private fun addBubble(message: String) {
        val tv = TextView(this).apply {
            text = message; textSize = 16f
            setTextColor(if (dark) Color.WHITE else Color.rgb(25,40,55))
            setPadding(12, 9, 12, 9)
            setBackgroundColor(if (dark) Color.rgb(44,58,75) else Color.WHITE)
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 4, 0, 4)
        log.addView(tv, lp)
    }

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "پیدا نشد"
    } catch (_: Exception) { "پیدا نشد" }

    private fun toast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        runCatching { server?.close() }
        peers.forEach { runCatching { it.socket.close() } }
        super.onDestroy()
    }
}
