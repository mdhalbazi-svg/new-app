package pk.lostfound.pakistan

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class MainActivity : AppCompatActivity() {
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null
    private var functions: FirebaseFunctions? = null
    private lateinit var content: LinearLayout
    private var loggedInEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFirebase()
        showHome()
    }

    private fun initFirebase() {
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            val app = FirebaseApp.initializeApp(this, options)
            if (app != null) {
                val appCheck = FirebaseAppCheck.getInstance(app)
                if (BuildConfig.DEBUG) appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
                else appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                auth = FirebaseAuth.getInstance(app)
                db = FirebaseFirestore.getInstance(app)
                functions = FirebaseFunctions.getInstance(app)
            }
        } catch (_: Exception) { auth = null }
    }

    private fun shell(title: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 20, 22, 20); setBackgroundColor(0xFFF7F8F6.toInt()) }
        root.addView(TextView(this).apply { text = title; textSize = 24f; setTextColor(0xFF145A45.toInt()); gravity = Gravity.CENTER; setPadding(0, 12, 0, 18) })
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        nav.addView(button("مرکزی صفحہ") { showHome() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(button("رپورٹ درج کریں") { showReportForm() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(button("اکاؤنٹ") { showAccount() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        setContentView(root)
    }

    private fun text(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(0xFF202820.toInt()); setPadding(4, 8, 4, 8)
    }
    private fun field(hint: String) = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(12, 10, 12, 10) }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
    private fun add(view: View) { content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }) }

    private fun showHome() {
        shell("گمشدہ چیز | Lost & Found Pakistan")
        add(text("گمشدہ یا ملنے والی چیز، گاڑی، پالتو جانور یا فرد کی اطلاع درج کریں۔", 17f))
        add(text("اہم حفاظتی ہدایت: گمشدہ بچے یا فوری خطرے کی صورت میں صرف ایپ پر انحصار نہ کریں۔ فوراً مقامی پولیس اور متعلقہ حکام سے رابطہ کریں۔"))
        if (auth?.currentUser == null) add(button("لاگ اِن / اکاؤنٹ بنائیں") { showAccount() })
        add(button("تازہ عوامی رپورٹس دیکھیں") { loadReports() })
        add(text("رپورٹس کی اشاعت انتظامیہ کی منظوری سے مشروط ہے۔ بچوں سے متعلق رپورٹس عوامی طور پر خودکار طور پر شائع نہیں ہوتیں۔", 14f))
    }

    private fun loadReports() {
        shell("عوامی رپورٹس")
        val store = db
        if (store == null) { add(text("Firebase کی ترتیب مکمل نہیں۔ README میں دی گئی ہدایات کے مطابق کنفیگریشن شامل کریں۔")); return }
        add(text("رپورٹس لوڈ ہو رہی ہیں…"))
        store.collection("reports").whereEqualTo("status", "published").limit(50).get()
            .addOnSuccessListener { snap ->
                content.removeAllViews()
                if (snap.isEmpty) add(text("فی الحال کوئی منظور شدہ رپورٹ موجود نہیں۔"))
                snap.documents.forEach { d ->
                    val category = d.getString("category") ?: "دیگر"
                    val type = if (d.getString("type") == "missing") "گمشدہ" else "ملی ہوئی"
                    val title = d.getString("title") ?: ""
                    val city = d.getString("city") ?: ""
                    add(text("$type • $category\n$title\n$city", 17f))
                    add(text("رابطے کی ذاتی معلومات عوامی فہرست میں ظاہر نہیں کی جاتیں۔", 13f))
                }
            }.addOnFailureListener { add(text("رپورٹس حاصل نہیں ہو سکیں: ${it.localizedMessage}")) }
    }

    private fun showAccount() {
        shell("اکاؤنٹ")
        val email = field("ای میل")
        val password = field("پاس ورڈ (کم از کم 6 حروف)").apply { inputType = 129 }
        add(email); add(password)
        val status = text(if (auth?.currentUser != null) "آپ لاگ اِن ہیں: ${auth?.currentUser?.email}" else "اکاؤنٹ بنانے یا لاگ اِن ہونے کے لیے ای میل درج کریں۔")
        add(status)
        add(button("نیا اکاؤنٹ بنائیں") {
            if (auth == null) { status.text = "Firebase کنفیگریشن موجود نہیں۔ README دیکھیں۔"; return@button }
            auth!!.createUserWithEmailAndPassword(email.text.toString().trim(), password.text.toString())
                .addOnSuccessListener { status.text = "اکاؤنٹ بن گیا۔ ای میل کی تصدیق مکمل کریں۔" }
                .addOnFailureListener { status.text = it.localizedMessage ?: "اکاؤنٹ نہیں بن سکا۔" }
        })
        add(button("لاگ اِن") {
            if (auth == null) { status.text = "Firebase کنفیگریشن موجود نہیں۔ README دیکھیں۔"; return@button }
            auth!!.signInWithEmailAndPassword(email.text.toString().trim(), password.text.toString())
                .addOnSuccessListener { status.text = "لاگ اِن کامیاب۔" }
                .addOnFailureListener { status.text = it.localizedMessage ?: "لاگ اِن نہیں ہو سکا۔" }
        })
        add(button("لاگ آؤٹ") { auth?.signOut(); showAccount() })
        add(text("اکاؤنٹ حذف کرنے کی درخواست فی الحال ایپ میں فعال نہیں؛ ریلیز سے پہلے README میں درج Data Deletion endpoint نافذ کرنا ضروری ہے۔", 13f))
    }

    private fun showReportForm() {
        shell("نئی رپورٹ")
        if (auth?.currentUser == null) { add(text("رپورٹ درج کرنے سے پہلے اکاؤنٹ میں لاگ اِن کریں۔")); add(button("اکاؤنٹ") { showAccount() }); return }
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("گمشدہ", "ملی ہوئی")) }
        val category = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("شخص", "بچہ", "موٹر سائیکل", "کار", "موبائل", "پالتو جانور", "دستاویز", "دیگر")) }
        val title = field("مختصر عنوان")
        val description = field("تفصیل (کم از کم 10 حروف)").apply { minLines = 3; gravity = Gravity.TOP }
        val province = field("صوبہ")
        val district = field("ضلع")
        val city = field("شہر")
        listOf(text("رپورٹ کی نوعیت"), type, text("قسم"), category, title, description, province, district, city).forEach { add(it) }
        val status = text("بچوں کی رپورٹ پہلے تصدیق اور منتظم کی منظوری کے عمل سے گزرے گی۔")
        add(status)
        add(button("رپورٹ جمع کریں") {
            val fn = functions
            if (fn == null) { status.text = "Firebase کنفیگریشن مکمل نہیں۔"; return@button }
            val cat = listOf("person","child","motorcycle","car","phone","pet","document","other")[category.selectedItemPosition]
            val data = hashMapOf<String, Any>(
                "type" to if (type.selectedItemPosition == 0) "missing" else "found",
                "category" to cat, "title" to title.text.toString().trim(),
                "description" to description.text.toString().trim(),
                "province" to province.text.toString().trim(), "district" to district.text.toString().trim(),
                "city" to city.text.toString().trim(), "eventAt" to System.currentTimeMillis()
            )
            fn.getHttpsCallable("createReport").call(data)
                .addOnSuccessListener { status.text = "رپورٹ جمع ہو گئی۔ منظوری کے بعد شائع ہوگی۔" }
                .addOnFailureListener { status.text = "رپورٹ جمع نہیں ہو سکی: ${it.localizedMessage}" }
        })
        add(text("اس ابتدائی ورژن میں تصویر اپلوڈ، فون OTP، نقشہ، براہِ راست رابطہ اور رپورٹ میں ترمیم شامل نہیں۔", 13f))
    }
}
