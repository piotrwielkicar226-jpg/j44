package pl.piotr.mailbox

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var credentials: CredentialStore
    private val executor = Executors.newSingleThreadExecutor()
    private var account: MailConfig? = null
    private var visibleMessages: List<MailMessage> = emptyList()
    private var loading = false
    private var inboxError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentials = CredentialStore(this)
        window.statusBarColor = Color.rgb(245, 247, 250)
        window.navigationBarColor = Color.rgb(245, 247, 250)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        account = credentials.load()
        if (account == null) {
            renderSetup()
        } else {
            renderInbox()
            refreshInbox()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun renderSetup(existing: MailConfig? = account) {
        val saved = existing ?: credentials.load()
        val page = newPage("Poczta", "Połącz swoją skrzynkę e-mail")
        addText(page, "Aplikacja łączy się bezpośrednio z usługą pocztową przez IMAP i SMTP. Usługa musi udostępniać te protokoły.", 15, Color.rgb(75, 85, 99))
        addSpace(page, 12)

        val email = addField(
            page,
            "Adres e-mail",
            saved?.email.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val password = addField(
            page,
            "Hasło aplikacji",
            "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            secret = true
        )
        val imapHost = addField(page, "Serwer IMAP", saved?.imapHost.orEmpty())
        val imapPort = addField(
            page,
            "Port IMAP (zwykle 993)",
            (saved?.imapPort ?: 993).toString(),
            InputType.TYPE_CLASS_NUMBER
        )
        val smtpHost = addField(page, "Serwer SMTP", saved?.smtpHost.orEmpty())
        val smtpPort = addField(
            page,
            "Port SMTP (587 STARTTLS lub 465 SSL)",
            (saved?.smtpPort ?: 587).toString(),
            InputType.TYPE_CLASS_NUMBER
        )
        addText(page, "Nie zapisujemy treści wiadomości lokalnie. Hasło jest szyfrowane na telefonie. Jeśli dostawca poczty oferuje hasło aplikacji, użyj go zamiast głównego hasła.", 13, Color.rgb(75, 85, 99))
        addSpace(page, 12)

        var connectButton: Button? = null
        connectButton = addButton(page, "Połącz i odbierz pocztę") {
            val secret = password.text.toString().ifBlank { saved?.password.orEmpty() }
            val config = MailConfig(
                email = email.text.toString().trim(),
                password = secret,
                imapHost = imapHost.text.toString().trim(),
                imapPort = imapPort.text.toString().toIntOrNull() ?: 993,
                smtpHost = smtpHost.text.toString().trim(),
                smtpPort = smtpPort.text.toString().toIntOrNull() ?: 587
            )
            if (config.email.isBlank() || config.password.isBlank() || config.imapHost.isBlank() || config.smtpHost.isBlank()) {
                toast("Uzupełnij adres, hasło aplikacji i oba serwery.")
                return@addButton
            }
            if (config.imapPort !in 1..65535 || config.smtpPort !in 1..65535) {
                toast("Sprawdź port IMAP i SMTP.")
                return@addButton
            }

            connectButton?.isEnabled = false
            executor.execute {
                try {
                    val messages = MailService.fetchInbox(config)
                    credentials.save(config)
                    account = config
                    visibleMessages = messages
                    inboxError = null
                    loading = false
                    runOnUiThread {
                        renderInbox()
                        toast("Połączono. Pobrano wiadomości: " + messages.size)
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        connectButton?.isEnabled = true
                        toast(error.localizedMessage ?: "Nie udało się połączyć z serwerem poczty.")
                    }
                }
            }
        }
        if (saved != null) {
            addButton(page, "Wyczyść zapisane konto") {
                credentials.clear()
                account = null
                visibleMessages = emptyList()
                renderSetup(null)
            }
        }
    }

    private fun refreshInbox() {
        val config = account ?: return
        loading = true
        inboxError = null
        renderInbox()
        executor.execute {
            try {
                visibleMessages = MailService.fetchInbox(config)
                inboxError = null
            } catch (error: Exception) {
                inboxError = error.localizedMessage ?: "Nie udało się pobrać wiadomości."
            }
            loading = false
            runOnUiThread { renderInbox() }
        }
    }

    private fun renderInbox() {
        val config = account ?: run {
            renderSetup(null)
            return
        }
        val page = newPage("Odebrane", config.email)
        addButton(page, "Odśwież skrzynkę") { refreshInbox() }
        addButton(page, "Napisz wiadomość") { showCompose(config) }
        addButton(page, "Ustawienia konta") { renderSetup(config) }

        if (loading) addText(page, "Pobieram wiadomości…", 15, Color.rgb(75, 85, 99))
        if (inboxError != null) addText(page, inboxError.orEmpty(), 14, Color.rgb(180, 45, 45))
        if (!loading && visibleMessages.isEmpty() && inboxError == null) {
            addText(page, "Skrzynka INBOX jest pusta.", 15, Color.rgb(75, 85, 99))
        }

        visibleMessages.forEach { message ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                setBackgroundColor(Color.WHITE)
                isClickable = true
                isFocusable = true
                setOnClickListener { showMessage(message) }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 10.dp()
            page.addView(card, params)
            addText(card, message.from + "  ·  " + message.date, 12, Color.rgb(96, 105, 120))
            addText(card, message.subject, 17, Color.rgb(20, 31, 50), bold = true)
            addText(card, message.body.take(140).ifBlank { "(brak treści tekstowej)" }, 14, Color.rgb(75, 85, 99))
        }
    }

    private fun showMessage(message: MailMessage) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
        }
        addText(content, message.from + "\n" + message.date, 13, Color.rgb(96, 105, 120))
        addSpace(content, 8)
        addText(content, message.body.ifBlank { "Wiadomość nie zawiera obsługiwanej treści tekstowej." }, 15, Color.rgb(35, 42, 52))
        AlertDialog.Builder(this)
            .setTitle(message.subject)
            .setView(content)
            .setPositiveButton("Zamknij", null)
            .show()
    }

    private fun showCompose(config: MailConfig) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 8.dp(), 18.dp(), 4.dp())
        }
        val recipient = addField(form, "Do", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val subject = addField(form, "Temat")
        val body = addField(form, "Treść wiadomości", multiline = true)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Nowa wiadomość")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Wyślij", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val to = recipient.text.toString().trim()
                if (to.isBlank()) {
                    toast("Wpisz adres odbiorcy.")
                    return@setOnClickListener
                }
                val mailSubject = subject.text.toString().trim()
                val mailBody = body.text.toString()
                dialog.dismiss()
                executor.execute {
                    try {
                        MailService.send(config, to, mailSubject, mailBody)
                        runOnUiThread {
                            toast("Wiadomość została wysłana.")
                            refreshInbox()
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            toast(error.localizedMessage ?: "Nie udało się wysłać wiadomości.")
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun newPage(title: String, subtitle: String): LinearLayout {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 24.dp(), 20.dp(), 28.dp())
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        scroll.addView(page)
        setContentView(scroll)
        addText(page, title, 28, Color.rgb(20, 31, 50), bold = true)
        addText(page, subtitle, 14, Color.rgb(96, 105, 120))
        addSpace(page, 18)
        return page
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Int = 16,
        color: Int = Color.BLACK,
        bold: Boolean = false
    ) {
        val view = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 4.dp(), 0, 4.dp())
        }
        parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addField(
        parent: LinearLayout,
        hint: String,
        value: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        secret: Boolean = false,
        multiline: Boolean = false
    ): EditText {
        val input = EditText(this).apply {
            this.hint = hint
            setText(value)
            this.inputType = inputType
            setSingleLine(!multiline)
            if (multiline) {
                minLines = 4
                gravity = Gravity.TOP or Gravity.START
            }
            if (secret) transformationMethod = PasswordTransformationMethod.getInstance()
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            setBackgroundColor(Color.WHITE)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 10.dp()
        parent.addView(input, params)
        return input
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 8.dp()
        parent.addView(button, params)
        return button
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(android.view.View(this), LinearLayout.LayoutParams(1, height.dp()))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
