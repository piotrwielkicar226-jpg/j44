package pl.piotr.mailbox

import java.text.DateFormat
import java.util.Date
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

 data class MailConfig(
    val email: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int
)

data class MailMessage(
    val from: String,
    val subject: String,
    val date: String,
    val body: String
)

object MailService {
    fun fetchInbox(config: MailConfig, limit: Int = 30): List<MailMessage> {
        val properties = Properties().apply {
            setProperty("mail.store.protocol", "imaps")
            setProperty("mail.imaps.ssl.enable", "true")
            setProperty("mail.imaps.ssl.checkserveridentity", "true")
            setProperty("mail.imaps.connectiontimeout", "15000")
            setProperty("mail.imaps.timeout", "15000")
            setProperty("mail.imaps.writetimeout", "15000")
        }
        val session = Session.getInstance(properties)
        val store = session.getStore("imaps")
        var inbox: Folder? = null
        try {
            store.connect(config.imapHost, config.imapPort, config.email, config.password)
            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val count = inbox.messageCount
            if (count <= 0) return emptyList()

            val first = (count - limit + 1).coerceAtLeast(1)
            return inbox.getMessages(first, count)
                .reversed()
                .map { message -> toMailMessage(message) }
        } finally {
            try {
                if (inbox?.isOpen == true) inbox.close(false)
            } catch (_: Exception) {
            }
            try {
                if (store.isConnected) store.close()
            } catch (_: Exception) {
            }
        }
    }

    fun send(config: MailConfig, recipient: String, subject: String, body: String) {
        val protocol = if (config.smtpPort == 465) "smtps" else "smtp"
        val prefix = "mail." + protocol
        val properties = Properties().apply {
            setProperty("mail.transport.protocol", protocol)
            setProperty(prefix + ".auth", "true")
            setProperty(prefix + ".ssl.checkserveridentity", "true")
            setProperty(prefix + ".connectiontimeout", "15000")
            setProperty(prefix + ".timeout", "15000")
            setProperty(prefix + ".writetimeout", "15000")
            if (config.smtpPort == 465) {
                setProperty(prefix + ".ssl.enable", "true")
            } else {
                setProperty(prefix + ".starttls.enable", "true")
                setProperty(prefix + ".starttls.required", "true")
            }
        }
        val session = Session.getInstance(properties)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.email))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient.trim(), false))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            sentDate = Date()
        }
        val transport = session.getTransport(protocol)
        try {
            transport.connect(config.smtpHost, config.smtpPort, config.email, config.password)
            transport.sendMessage(message, message.allRecipients)
        } finally {
            try {
                transport.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun toMailMessage(message: Message): MailMessage {
        val sender = message.from?.joinToString { address ->
            (address as? InternetAddress)?.address ?: address.toString()
        } ?: "Nieznany nadawca"
        val dateValue = message.receivedDate ?: message.sentDate
        val date = dateValue?.let { DateFormat.getDateTimeInstance().format(it) } ?: ""
        return MailMessage(
            from = sender,
            subject = message.subject ?: "(bez tematu)",
            date = date,
            body = extractPlainText(message).trim()
        )
    }

    private fun extractPlainText(part: Part): String {
        return try {
            when {
                part.isMimeType("text/plain") -> part.content?.toString().orEmpty()
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as? Multipart ?: return ""
                    var result = ""
                    for (index in 0 until multipart.count) {
                        val text = extractPlainText(multipart.getBodyPart(index))
                        if (text.isNotBlank()) {
                            result = text
                            break
                        }
                    }
                    result
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
