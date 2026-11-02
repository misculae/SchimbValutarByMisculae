package ro.misculae.schimbvalutar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ExchangeApp() } }
    }
}

private data class BnrRate(val code: String, val value: Double)

@Composable
private fun ExchangeApp() {
    var amount by remember { mutableStateOf("100") }
    var selected by remember { mutableStateOf("USD") }
    var bnr by remember { mutableStateOf<List<BnrRate>>(emptyList()) }
    var bnrDate by remember { mutableStateOf("Se încarcă…") }
    var message by remember { mutableStateOf("Se preiau automat cursurile oficiale BNR.") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            message = "Se preiau cursurile BNR…"
            try {
                val pair = withContext(Dispatchers.IO) { fetchBnr() }
                bnr = pair.first.filter { it.code in setOf("USD", "EUR", "GBP", "XAU") }
                bnrDate = pair.second
                val missing = listOf("USD", "EUR", "GBP", "XAU").filter { code -> bnr.none { it.code == code } }
                message = if (missing.isEmpty()) "Cursurile au fost actualizate automat." else "BNR nu a publicat în acest feed: ${missing.joinToString()}"
            } catch (e: Exception) {
                message = "Eroare BNR: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val currentRate = bnr.firstOrNull { it.code == selected }?.value
    val numericAmount = amount.replace(',', '.').toDoubleOrNull()
    val converted = if (currentRate != null && numericAmount != null) numericAmount * currentRate else null

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("Schimb Valutar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("by Misculae · cursuri oficiale BNR", color = MaterialTheme.colorScheme.primary)
                Text("Conversie automată în lei, folosind cursul BNR publicat.")
            }
            item {
                SectionTitle("CONVERTOR VALUTAR")
                Text("Alege valuta")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("USD", "EUR", "GBP", "XAU").forEach { code ->
                        FilterChip(
                            selected = selected == code,
                            onClick = { selected = code },
                            label = { Text(code) }
                        )
                    }
                }
                NumberField(if (selected == "XAU") "Cantitate în grame" else "Suma în $selected", amount) { amount = it }
                Text(
                    when (selected) {
                        "XAU" -> "1 gram aur (BNR)"
                        else -> "1 $selected"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    currentRate?.let { String.format(Locale.US, "%.4f RON", it) } ?: "Curs indisponibil",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                Text("REZULTAT ÎN LEI", fontWeight = FontWeight.Bold)
                Text(
                    converted?.let { String.format(Locale("ro", "RO"), "%,.2f RON", it) } ?: "Așteaptă cursul BNR sau introdu o sumă validă.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Calculul se actualizează automat când schimbi suma sau valuta.")
            }
            item {
                SectionTitle("CURSURI BNR")
                Text("Data cursului: $bnrDate")
                Button(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "SE ACTUALIZEAZĂ…" else "ACTUALIZEAZĂ CURSURILE")
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            items(listOf("USD", "EUR", "GBP", "XAU")) { code ->
                val rate = bnr.firstOrNull { it.code == code }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (code == "XAU") "Aur – gram" else code, fontWeight = FontWeight.SemiBold)
                            Text(if (code == "XAU") "Preț oficial gram aur" else "Curs oficial BNR")
                        }
                        Text(
                            rate?.let { String.format(Locale.US, "%.4f RON", it.value) } ?: "—",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            item {
                SectionTitle("IMPORTANT")
                Text("Cursurile BNR sunt cursuri oficiale de referință, nu oferte garantate de cumpărare sau vânzare la bancă ori la casa de schimb.")
                Text("Pentru comparația ofertelor comerciale și calculul câștigului net după comisioane vom folosi separat cursurile efective ale băncilor, caselor de schimb și Revolut.")
                Text("Sursa: https://curs.bnr.ro/nbrfxrates.xml")
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun fetchBnr(): Pair<List<BnrRate>, String> {
    val connection = (URL("https://curs.bnr.ro/nbrfxrates.xml").openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SchimbValutarByMisculae/1.0")
        setRequestProperty("Accept", "application/xml,text/xml,*/*")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("Serverul BNR a răspuns cu HTTP $code")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        var date = ""
        val rates = mutableMapOf<String, Double>()
        var currentCode: String? = null
        var multiplier = 1.0
        connection.inputStream.buffered().use { input ->
            parser.setInput(input, "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                        "Cube" -> date = parser.getAttributeValue(null, "date") ?: date
                        "Rate" -> {
                            currentCode = parser.getAttributeValue(null, "currency")
                            multiplier = parser.getAttributeValue(null, "multiplier")?.toDoubleOrNull() ?: 1.0
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (currentCode != null) {
                        val value = parser.text.trim().replace(',', '.').toDoubleOrNull()
                        if (value != null) rates[currentCode!!] = value / multiplier
                    }
                    XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "Rate") {
                        currentCode = null
                        multiplier = 1.0
                    }
                }
                event = parser.next()
            }
        }
        if (rates.isEmpty() || date.isBlank()) throw IllegalStateException("Feed-ul XML nu conține cursuri sau data publicării.")
        val normalizedDate = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            SimpleDateFormat("dd.MM.yyyy", Locale("ro", "RO")).format(parsed!!)
        } catch (_: Exception) { date }
        return rates.map { BnrRate(it.key, it.value) }.sortedBy { it.code } to normalizedDate
    } finally {
        connection.disconnect()
    }
}
