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
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ExchangeApp() } }
    }
}

private data class Quote(val name: String, val rate: Double, val feeUsd: Double, val note: String)
private data class BnrRate(val code: String, val value: Double)

@Composable
private fun ExchangeApp() {
    var amount by remember { mutableStateOf("5000") }
    var btPercent by remember { mutableStateOf("0.9") }
    var revolutFee by remember { mutableStateOf("18") }
    var revolutRate by remember { mutableStateOf("") }
    var bankRate by remember { mutableStateOf("") }
    var cashRate by remember { mutableStateOf("") }
    var bnr by remember { mutableStateOf<List<BnrRate>>(emptyList()) }
    var bnrDate by remember { mutableStateOf("Neactualizat") }
    var message by remember { mutableStateOf("Cursurile comerciale se introduc/verifică din sursa afișată. Nu folosim cotații inventate.") }
    var results by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun calculate() {
        val usd = amount.toDoubleOrNull() ?: 0.0
        val btFee = btPercent.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val revFee = revolutFee.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val out = mutableListOf<Pair<String, Double>>()
        val bRate = bankRate.toDoubleOrNull()
        val cRate = cashRate.toDoubleOrNull()
        val rRate = revolutRate.toDoubleOrNull()
        if (bRate != null && bRate > 0) out += "Bancă – schimb în cont" to usd * bRate
        if (cRate != null && cRate > 0) out += "Casă de schimb – numerar BT" to (usd * (1.0 - btFee / 100.0)) * cRate
        if (rRate != null && rRate > 0 && usd > revFee) out += "Revolut – transfer USD" to (usd - revFee) * rRate
        results = out.sortedByDescending { it.second }
        message = if (out.isEmpty()) "Introdu cel puțin un curs de cumpărare USD pentru a calcula." else "Rezultatele folosesc cursurile introduse de tine; verifică disponibilitatea și comisioanele înainte de tranzacție."
    }

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("Schimb Valutar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("by Misculae · București", color = MaterialTheme.colorScheme.primary)
                Text("Compară cât primești NET în lei, nu doar cursul afișat.")
            }
            item {
                SectionTitle("CALCULATOR USD → RON")
                NumberField("Suma în USD", amount) { amount = it }
                NumberField("Comision retragere BT (%)", btPercent) { btPercent = it }
                NumberField("Comision transfer Revolut (USD)", revolutFee) { revolutFee = it }
                NumberField("Curs bancă: cumpărare USD (RON)", bankRate) { bankRate = it }
                NumberField("Curs casă schimb: cumpărare USD (RON)", cashRate) { cashRate = it }
                NumberField("Curs Revolut USD → RON", revolutRate) { revolutRate = it }
                Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("CALCULEAZĂ ACUM") }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            item { SectionTitle("CLASAMENT – LEI NET") }
            if (results.isEmpty()) item { Text("Completează unul sau mai multe cursuri și apasă CALCULEAZĂ.") }
            items(results) { (name, value) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale("ro", "RO"), "%,.2f RON", value), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                SectionTitle("CURS OFICIAL BNR")
                Text("Sursa: curs.bnr.ro · curs de referință, nu ofertă garantată de schimb.")
                Text("Data: $bnrDate")
                Button(onClick = {
                    scope.launch {
                        message = "Se preia cursul BNR…"
                        try {
                            val pair = withContext(Dispatchers.IO) { fetchBnr() }
                            bnr = pair.first
                            bnrDate = pair.second
                            message = "Curs BNR actualizat. Cotațiile comerciale se verifică separat."
                        } catch (e: Exception) {
                            message = "Nu am putut prelua BNR: ${e.localizedMessage ?: "eroare de conexiune"}"
                        }
                    }
                }) { Text("ACTUALIZEAZĂ CURS BNR") }
            }
            items(bnr) { rate ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(rate.code, fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.4f RON", rate.value))
                }
            }
            item {
                SectionTitle("SURSE ȘI VERIFICĂRI")
                Text("• BNR: curs oficial XML – curs.bnr.ro")
                Text("• Bănci: verifică rubrica «cumpărare USD»; condițiile pot depinde de pachet și canal.")
                Text("• Case de schimb: Valutare.ro – confirmă telefonic cursul, suma acceptată și existența numerarului.")
                Text("• BankNews: reper comparativ pentru cursurile bancare.")
                Text("• Revolut: introdu cursul efectiv afișat în aplicația ta.")
                Text("Valorile de comision sunt editabile. Taxa Revolut de 18 USD este doar valoarea ta implicită, nu o garanție universală.")
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

private fun fetchBnr(): Pair<List<BnrRate>, String> {
    val connection = URL("https://www.bnr.ro/nbrfxrates.xml").openConnection()
    connection.connectTimeout = 12000
    connection.readTimeout = 12000
    connection.setRequestProperty("User-Agent", "SchimbValutarByMisculae/1.0")
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    connection.getInputStream().use { parser.setInput(it, "UTF-8") }
    var date = ""
    val rates = mutableListOf<BnrRate>()
    var currentCode: String? = null
    var inRate = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "Cube" -> date = parser.getAttributeValue(null, "date") ?: date
                "Rate" -> { currentCode = parser.getAttributeValue(null, "currency"); inRate = true }
            }
            XmlPullParser.TEXT -> if (inRate && currentCode != null) {
                val value = parser.text.trim().replace(',', '.').toDoubleOrNull()
                if (value != null) rates += BnrRate(currentCode!!, value)
            }
            XmlPullParser.END_TAG -> if (parser.name == "Rate") { currentCode = null; inRate = false }
        }
        event = parser.next()
    }
    val normalizedDate = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        SimpleDateFormat("dd.MM.yyyy", Locale("ro", "RO")).format(d!!)
    } catch (_: Exception) { date.ifBlank { "necunoscută" } }
    return rates.sortedBy { it.code } to normalizedDate
}
