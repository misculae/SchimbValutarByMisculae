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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { ExchangeApp() } } }
}
private data class Offer(val name:String,val channel:String,val buy:Double,val updated:String,val source:String,val isBt:Boolean=false)
private data class RatesData(val bnr:Map<String,Double>,val offers:List<Offer>,val updated:String)

@Composable private fun ExchangeApp() {
 var amount by remember { mutableStateOf("5000") }
 var currency by remember { mutableStateOf("USD") }
 var bnr by remember { mutableStateOf<Map<String,Double>>(emptyMap()) }
 var offers by remember { mutableStateOf<List<Offer>>(emptyList()) }
 var updated by remember { mutableStateOf("—") }
 var status by remember { mutableStateOf("Se încarcă sursele…") }
 var loading by remember { mutableStateOf(false) }
 var btFeeOn by remember { mutableStateOf(true) }
 var btPercent by remember { mutableStateOf("0.9") }
 var btMinimum by remember { mutableStateOf("9") }
 var revolutFee by remember { mutableStateOf("18") }
 var revolutRate by remember { mutableStateOf("") }
 val scope = rememberCoroutineScope()
 fun refresh() { scope.launch {
  loading=true; status="Preiau cursurile actualizate…"
  try {
   val data=withContext(Dispatchers.IO) { loadRates(currency) }
   bnr=data.bnr; offers=data.offers; updated=data.updated
   status="Actualizat: "+data.offers.size+" oferte. Băncile + casele de schimb."
  } catch(e:Exception) { status="Eroare la preluarea datelor: "+(e.localizedMessage ?: e.javaClass.simpleName) }
  finally { loading=false }
 } }
 LaunchedEffect(currency) { refresh() }
 val qty=amount.replace(',','.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
 val pct=btPercent.replace(',','.').toDoubleOrNull() ?: 0.9
 val minFee=btMinimum.replace(',','.').toDoubleOrNull() ?: 9.0
 val feeUsd=revolutFee.replace(',','.').toDoubleOrNull() ?: 18.0
 val usdBnr=bnr["USD"] ?: 0.0
 val fixedFeeRon=feeUsd*usdBnr
 data class Result(val offer:Offer,val gross:Double,val fee:Double,val net:Double)
 val ranked=offers.map { o ->
  val gross=qty*o.buy
  val fee=if(o.isBt && btFeeOn) maxOf(gross*pct/100.0,minFee) else 0.0
  Result(o,gross,fee,gross-fee)
 }.filter { it.net>0 }.sortedByDescending { it.net }
 val best=ranked.firstOrNull()
 Scaffold { pad ->
  LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   item {
    Spacer(Modifier.height(12.dp))
    Text("Schimb Valutar",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
    Text("by Misculae · compară suma netă",color=MaterialTheme.colorScheme.primary)
    Text("Cât primești efectiv în lei după comisioane.")
   }
   item {
    Heading("1. SUMA ȘI VALUTA")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("USD","EUR","GBP").forEach { c -> FilterChip(selected=currency==c,onClick={currency=c},label={Text(c)}) } }
    Field("Suma de schimbat",amount) { amount=it }
    Text("BNR "+currency+": "+(bnr[currency]?.let { rate(it)+" RON" } ?: "se încarcă"))
    Text("Actualizat: "+updated,style=MaterialTheme.typography.bodySmall)
    Button(onClick={refresh},enabled=!loading,modifier=Modifier.fillMaxWidth()) { Text(if(loading) "SE ACTUALIZEAZĂ…" else "ACTUALIZEAZĂ CURSURILE") }
    Text(status,style=MaterialTheme.typography.bodySmall)
   }
   item {
    Heading("2. COMISIOANE")
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(btFeeOn,{btFeeOn=it}); Text("Aplică retragerea numerar BT") }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Column(Modifier.weight(1f)) { Field("BT comision %",btPercent){btPercent=it} }
     Column(Modifier.weight(1f)) { Field("Minim lei",btMinimum){btMinimum=it} }
    }
    Field("Revolut comision fix (USD)",revolutFee){revolutFee=it}
    Field("Curs Revolut RON / 1 "+currency+" (din aplicație)",revolutRate){revolutRate=it}
    val rr=revolutRate.replace(',','.').toDoubleOrNull()
    if(rr!=null && rr>0 && qty>0) Text("Revolut estimat: "+cash(qty*rr-fixedFeeRon)+" RON net (comision fix estimat "+cash(fixedFeeRon)+" RON)")
    Text("Cursul Revolut trebuie introdus din aplicație: este personalizat și nu există o cotație publică garantată. Comisionul de 18 USD rămâne editabil.",style=MaterialTheme.typography.bodySmall)
   }
   item {
    Heading("3. CEA MAI BUNĂ OFERTĂ")
    if(best!=null) Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
     Column(Modifier.padding(16.dp)) {
      Text(best.offer.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
      Text(best.offer.channel+" · "+best.offer.source)
      Text(cash(best.net)+" RON",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
      Text("Curs "+rate(best.offer.buy)+" · comision "+cash(best.fee)+" RON")
     }
    } else Text("Nu există încă oferte disponibile.")
   }
   item { Heading("4. CLASAMENT – LEI NET") }
   items(ranked) { r ->
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
     Text(r.offer.name,fontWeight=FontWeight.SemiBold)
     Text(r.offer.channel+" · "+r.offer.source,style=MaterialTheme.typography.bodySmall)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Curs "+rate(r.offer.buy)); Text(cash(r.net)+" RON",fontWeight=FontWeight.Bold) }
     Text("Brut "+cash(r.gross)+" · comision "+cash(r.fee)+" RON",style=MaterialTheme.typography.bodySmall)
     if(r.offer.updated.isNotBlank()) Text("Actualizat: "+r.offer.updated,style=MaterialTheme.typography.labelSmall)
    } }
   }
   item {
    Heading("SURSE ȘI NOTE")
    Text("Bănci: API cursuri bancare publice (13 instituții). Case de schimb: Valutare.ro – București. Curs oficial: BNR.")
    Text("Ofertele de la case pot avea praguri minime și disponibilitate limitată. Verifică telefonic înainte de deplasare.")
    Text("BT: comisionul de retragere este aplicat separat, cu procent și minim configurabile. Cursul din cont diferă de cel de la ghișeu.")
    Text("Revolut: cursul și comisionul depind de plan, plafonul lunar și momentul schimbului.")
    Text("Surse: cursdevaluta.ro · valutare.ro · BankNews.ro · BNR · Revolut")
    Spacer(Modifier.height(24.dp))
   }
  }
 }
}
@Composable private fun Heading(t:String) { Text(t,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp)) }
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit) {
 OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
}
private fun loadRates(currency:String):RatesData {
 val bnr=fetchBnr()
 val offers=fetchBanks(currency)+fetchExchanges(currency)
 return RatesData(bnr,offers,SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("ro","RO")).format(Date()))
}
private fun fetchBnr():Map<String,Double> {
 val root=JSONObject(getText("https://cursdevaluta.ro/api/rates"))
 val rates=root.optJSONObject("rates") ?: JSONObject()
 return listOf("USD","EUR","GBP").mapNotNull { c -> val v=rates.optDouble(c,Double.NaN); if(v.isFinite()&&v>0) c to v else null }.toMap()
}
private fun fetchBanks(currency:String):List<Offer> {
 val root=JSONObject(getText("https://cursdevaluta.ro/api/bank-rates"))
 val arr=root.optJSONObject("currencies")?.optJSONArray(currency) ?: return emptyList()
 val out=mutableListOf<Offer>()
 for(i in 0 until arr.length()) {
  val x=arr.optJSONObject(i) ?: continue
  val buy=x.optDouble("buy",0.0); if(buy<=0) continue
  val bank=x.optString("bank","Bancă")
  out.add(Offer(bank,x.optString("channel","Bancă"),buy,root.optString("date",""),"Bănci · cursdevaluta.ro",bank.contains("Transilvania",true)))
 }
 return out
}
private fun fetchExchanges(currency:String):List<Offer> {
 val slug=when(currency) { "USD"->"usd"; "EUR"->"eur"; else->"gbp" }
 val address="https://www.valutare.ro/curs/curs-valutar-"+slug+"-case-de-schimb-valutar-bucuresti.html"
 val doc=Jsoup.parse(getText(address))
 val out=mutableListOf<Offer>()
 for(row in doc.select("table tr")) {
  val c=row.select("td").map { it.text().trim() }
  if(c.size<4) continue
  val name=c[0]; val area=c.getOrNull(1).orEmpty(); val buy=parseRate(c.getOrNull(2).orEmpty())
  if(name.isBlank()||buy<=0||!area.contains("Bucure",true)) continue
  out.add(Offer(name,area,buy,c.getOrNull(4).orEmpty(),"Case de schimb · Valutare.ro"))
 }
 return out.distinctBy { it.name.lowercase(Locale.ROOT) }
}
private fun parseRate(s:String):Double=s.replace(",",".").replace(Regex("[^0-9.]"),"").toDoubleOrNull() ?: 0.0
private fun getText(address:String):String {
 val c=(URL(address).openConnection() as HttpURLConnection).apply {
  connectTimeout=18000; readTimeout=18000; requestMethod="GET"
  setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14) SchimbValutarByMisculae/2.0")
  setRequestProperty("Accept","application/json,text/html,application/xml,*/*")
 }
 try { val code=c.responseCode; if(code !in 200..299) throw IllegalStateException("HTTP "+code+" la "+URL(address).host); return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
 finally { c.disconnect() }
}
private fun cash(v:Double)=String.format(Locale("ro","RO"),"%,.2f",v)
private fun rate(v:Double)=String.format(Locale.US,"%.4f",v)
