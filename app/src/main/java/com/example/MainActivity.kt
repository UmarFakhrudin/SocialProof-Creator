package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import coil.compose.AsyncImage
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.ui.theme.MyApplicationTheme
import java.io.OutputStream
import java.util.UUID

// ==========================================
// DATA MODELS & ENUMS
// ==========================================

enum class SocialPlatform(val displayName: String, val icon: String) {
    WHATSAPP_CHAT("WhatsApp Chat", "💬"),
    WHATSAPP_STATUS("WhatsApp Status", "📸"),
    INSTAGRAM_DM("Instagram DM", "✉️"),
    INSTAGRAM_COMMENT("Instagram Komen", "📝"),
    FACEBOOK_MESSENGER("FB Messenger", "⚡"),
    FACEBOOK_COMMENT("FB Komen", "🗣️"),
    TIKTOK_COMMENT("TikTok Komen", "🎵"),
    TIKTOK_DM("TikTok DM", "📬")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isSelf: Boolean = true, // true = Kirim, false = Terima
    val text: String = "",
    val time: String = "10:45",
    val status: String = "READ" // SENT (tunggal abu), DELIVERED (ganda abu), READ (ganda biru)
)

data class SocialComment(
    val id: String = UUID.randomUUID().toString(),
    val username: String = "user_name",
    val text: String = "",
    val time: String = "3m saja",
    val likesCount: String = "24",
    val isVerified: Boolean = false,
    val isCreator: Boolean = false,
    val avatarPreset: Int = 0
)

// ==========================================
// MAIN ACTIVITY
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SocialProofCreatorApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// ==========================================
// MAIN WORKSPACE INTERFACE
// ==========================================

@Composable
fun SocialProofCreatorApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // -- App Workspace States --
    var currentPlatform by remember { mutableStateOf(SocialPlatform.WHATSAPP_CHAT) }
    var profileName by remember { mutableStateOf("Admin Toko") }
    var username by remember { mutableStateOf("admin_sukses") }
    var profileSubtitle by remember { mutableStateOf("Online") }
    var isVerified by remember { mutableStateOf(true) }
    var isOnline by remember { mutableStateOf(true) }
    var isDarkPreview by remember { mutableStateOf(false) }

    // Custom avatar image selected from launcher
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var avatarPresetIndex by remember { mutableStateOf(0) }

    // Platform Post/Caption settings
    var generalPostCaption by remember { mutableStateOf("Dapatkan DISKON 50% khusus hari ini saja sist! Hubungi kami segera sebelum kehabisan slot promo gila-gilaan ini! 💸🔥") }
    var statusBgGradientIndex by remember { mutableStateOf(0) } // for WhatsApp Status background

    // Chat Messages list state
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(isSelf = false, text = "Halo kak, diskon produk herbalnya masih ada?", time = "09:40"),
                ChatMessage(isSelf = true, text = "Halo sista! Masih ada kak, khusus hari ini diskon 50% + gratis ongkir seluruh Indonesia 💚", time = "09:42", status = "READ"),
                ChatMessage(isSelf = false, text = "Wah beneran kak? Aku mau pesan 3 botol sekarang ya!", time = "09:43"),
                ChatMessage(isSelf = true, text = "Siap diproses segera kak! Isi format pemesanan sekarang ya 🙏✨", time = "09:45", status = "READ")
            )
        )
    }

    // Comments list state
    var comments by remember {
        mutableStateOf(
            listOf(
                SocialComment(username = "clara_safitri", text = "Ini beneran ampuh banget, awalnya ragu pas dicoba flek hitam langsung pudar semua! 😍✨", time = "2m lalu", likesCount = "142", isVerified = false, avatarPreset = 1),
                SocialComment(username = "budi_hustler", text = "Baru pertama kali beli tapi pelayanannya ramah banget. Paket nyampe secepat kilat! 👍", time = "15m lalu", likesCount = "52", isVerified = true, avatarPreset = 4),
                SocialComment(username = "skincare_hunter", text = "Udah repeat order 3 kali di toko ini, hasilnya selalu memuaskan & dapet bonus mulu", time = "1h lalu", likesCount = "89", isVerified = false, isCreator = true, avatarPreset = 5)
            )
        )
    }

    // Active screen workspace tab
    var activeTab by remember { mutableStateOf(0) } // 0 = Platform/Profil, 1 = Pesan/Komentar, 2 = Ekspor Studio

    // Image Picker Launcher
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUri = uri.toString()
        }
    }

    // ComposeView reference to capture to Bitmap
    var composeViewReference by remember { mutableStateOf<ComposeView?>(null) }

    // Gradient Background values for WA Status
    val statusBgGradients = listOf(
        Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))), // Instagram Sunset
        Brush.linearGradient(listOf(Color(0xFF1E3C72), Color(0xFF2A5298))), // Deep Blue
        Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))), // Midnight Slate
        Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))), // Organic Green
        Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))  // Red Love
    )

    // Function to set up default inputs based on selected platforms to make customization lightning-fast
    fun applyPlatformDefaults(platform: SocialPlatform) {
        currentPlatform = platform
        when (platform) {
            SocialPlatform.WHATSAPP_CHAT -> {
                profileName = "Admin Herbal Care"
                profileSubtitle = "Online"
                isVerified = true
                isOnline = true
                chatMessages = listOf(
                    ChatMessage(isSelf = false, text = "Halo kak, diskon produk herbalnya masih ada?", time = "09:40"),
                    ChatMessage(isSelf = true, text = "Halo sista! Masih ada kak, khusus hari ini diskon 50% + gratis ongkir seluruh Indonesia 💚", time = "09:42", status = "READ"),
                    ChatMessage(isSelf = false, text = "Wah beneran kak? Aku mau pesan 3 botol sekarang ya!", time = "09:43"),
                    ChatMessage(isSelf = true, text = "Siap diproses segera kak! Isi format pemesanan sekarang ya 🙏✨", time = "09:45", status = "READ")
                )
            }
            SocialPlatform.WHATSAPP_STATUS -> {
                profileName = "Owner Cantik Skincare"
                profileSubtitle = "Status Whatsapp"
                isVerified = false
                generalPostCaption = "Alhamdulillah pengiriman hari ini ludes ratusan botol! Makasih ya cust setianya owner. Slot diskon sisa 3 orang lagi malam ini ya, checkout buruan! 💸🔥🚀"
            }
            SocialPlatform.INSTAGRAM_DM -> {
                profileName = "Nabila Putri"
                username = "nabila_putri"
                profileSubtitle = "Aktif baru saja"
                isVerified = true
                isOnline = true
                chatMessages = listOf(
                    ChatMessage(isSelf = false, text = "Kak, obatnya beneran manjur banget! Jerawatku kempes dalam 2 hari aja, sumpah bahagia banget!! 😭❤️", time = "Kemarin"),
                    ChatMessage(isSelf = true, text = "Alhamdulillah!! Seneng banget dengernya sethree! Terus konsisten ya kak, ditunggu kiriman progress fotonya nanti 🥰", time = "10:05", status = "READ"),
                    ChatMessage(isSelf = false, text = "Iya kak, siap! Bakal langganan terus sih ini mah recommended bgt!!", time = "10:06")
                )
            }
            SocialPlatform.INSTAGRAM_COMMENT -> {
                profileName = "Bright & Glow Official"
                username = "brightglow_id"
                generalPostCaption = "Yakin gamau nyobain serum glowing no 1 di Indonesia? Sekali pakai langsung keliatan hasilnya! Cek link di bio kami 💫 #glowup #skincareglowing"
                comments = listOf(
                    SocialComment(username = "anisa_beautyspot", text = "Ini beneran recommended banget, kemaren iseng beli diskon dan beneran cocok! Muka jadi cerah banget ✨", time = "2j lalu", likesCount = "388", isVerified = false, avatarPreset = 1),
                    SocialComment(username = "fahmi_adventurer", text = "Sukses terus usahanya kak, istri saya suka banget beneran mulus katanya hehe", time = "4j lalu", likesCount = "156", isVerified = true, avatarPreset = 0),
                    SocialComment(username = "clara_putri", text = "Gak nyesel beli 2 paket sekaligus kemarin, dapet harga coret + bonus sabun wajah pula!", time = "5j lalu", likesCount = "45", isVerified = false, avatarPreset = 2)
                )
            }
            SocialPlatform.FACEBOOK_MESSENGER -> {
                profileName = "Customer Support"
                profileSubtitle = "Aktif di Messenger"
                isVerified = true
                isOnline = true
                chatMessages = listOf(
                    ChatMessage(isSelf = false, text = "Halo sist, pengiriman ke Malang kota gratis ongkir gak?", time = "14:15"),
                    ChatMessage(isSelf = true, text = "Halo sist! Iya betul sekali ke Malang Kota GRATIS ONGKIR tanpa minimal pembelian hari ini ya 💖", time = "14:16", status = "READ"),
                    ChatMessage(isSelf = false, text = "Aaaa mantap! Aku transfer sekarang ya sist, ditunggu paketan saya", time = "14:18")
                )
            }
            SocialPlatform.FACEBOOK_COMMENT -> {
                profileName = "Grup Bisnis Ibu Rumah Tangga"
                username = "grup_bisnis_irt"
                generalPostCaption = "Bagaimana saya menghasilkan 15 Juta sebulan hanya dari dapur sambil jagain anak. Caranya sangat praktis diajarkan gratis! Simak testimoninya di bawah..."
                comments = listOf(
                    SocialComment(username = "Siti Khotimah", text = "Alhamdulillah setelah ikut bimbingan materi kemarin kelas gratisnya, closingan pertama langsung pecah telor! Terima kasih mentor! 🙏", time = "1j lalu", likesCount = "89", isVerified = false, avatarPreset = 1),
                    SocialComment(username = "Dewi Susanti", text = "Awalnya ragu, pas dicoba bener-bener dipandu step-by-step sampe dapet database pembeli. Mantap berkah banget programnya!", time = "3j lalu", likesCount = "142", isVerified = false, avatarPreset = 3)
                )
            }
            SocialPlatform.TIKTOK_COMMENT -> {
                profileName = "fahmimedia"
                username = "fahmimedia"
                generalPostCaption = "Trik rahasia FYP dalam 24 jam saja terbukti berhasil! Jualan makin laris manis #fypシ #digitalmarketing #affiliate"
                comments = listOf(
                    SocialComment(username = "foryou_hustler", text = "Udah aku praktekin barusan dan salah satu video langsung tembus 120k views! Gila valid banget metodenya! 🤫💥", time = "1j lalu", likesCount = "1.2K", isVerified = true, isCreator = true, avatarPreset = 4),
                    SocialComment(username = "chika_mulia", text = "Makasih tipsnya bang, sangat membantu buat pemula kayak aku yang baru mau jualan di Tiktok", time = "3j lalu", likesCount = "420", isVerified = false, avatarPreset = 5),
                    SocialComment(username = "abdul_shopee", text = "Daging banget ilmunya bang! Rugi parah sih yang gak nonton sampe abis videonya", time = "5j lalu", likesCount = "88", isVerified = false, avatarPreset = 0)
                )
            }
            SocialPlatform.TIKTOK_DM -> {
                profileName = "Andika Pratama"
                username = "andika.pratama"
                profileSubtitle = "Aktif"
                isVerified = true
                isOnline = true
                chatMessages = listOf(
                    ChatMessage(isSelf = false, text = "Bang, template sosial proof buatan abang beneran ngebantu naikin omset olshopku! Closingan naik 2x lipat!", time = "16:10"),
                    ChatMessage(isSelf = true, text = "Mantap! Senang dengarnya bang, manfaatkan terus buat promosi ya! Sukses terus!", time = "16:12", status = "READ"),
                    ChatMessage(isSelf = false, text = "Dendeng daging bgt dah makasih abang ku", time = "16:13")
                )
            }
        }
    }

    // ==========================================
    // WORKSPACE LAYOUT CONTAINER
    // ==========================================
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FC)) // High Density slate background
    ) {
        // App Elegant Header (High Density Premium Style)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // M3 Verified Icon Box
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF00A884), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SocialProof Creator",
                            color = Color(0xFF1C1B1F),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "High-Fidelity Mockup Studio",
                            color = Color(0xFF44474E),
                            fontSize = 11.sp
                        )
                    }
                }

                // Header Action (Settings-style Toggle)
                IconButton(
                    onClick = { activeTab = 2 },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFF1F5F9), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings / Go to Preview",
                        tint = Color(0xFF44474E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFE1E3E8))
        }

        // Platform Selection Carousel (High Density Chips Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(Color(0xFFF7F9FC))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialPlatform.values().forEach { platform ->
                val isSelected = currentPlatform == platform
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFB1C5FF) else Color(0xFFC4C6D0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isSelected) Color(0xFFD8E2FF) else Color.White
                        )
                        .clickable {
                            applyPlatformDefaults(platform)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = platform.icon, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = platform.displayName,
                            color = if (isSelected) Color(0xFF001A41) else Color(0xFF44474E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Navigation Tabs (High Density Styling)
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.White,
            contentColor = Color(0xFF0061A4),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = Color(0xFF0061A4)
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Profil & Preset", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp)) },
                selectedContentColor = Color(0xFF0061A4),
                unselectedContentColor = Color(0xFF44474E)
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Edit Isi Konten", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                selectedContentColor = Color(0xFF0061A4),
                unselectedContentColor = Color(0xFF44474E)
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Pratinjau Live 📸", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp)) },
                selectedContentColor = Color(0xFF0061A4),
                unselectedContentColor = Color(0xFF44474E)
            )
        }

        // ==========================================
        // WORKSPACE SCROLL CONTENT BY ACTIVE TAB
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                0 -> {
                    // TAB 0: PROFILE & PRESETS CONFIGURATION
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Profile Banner Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "⚙️ Kustomisasi Info Profil",
                                    color = Color(0xFF1C1B1F),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                // Avatar Upload and Presets Selector
                                Text("Foto Profil Mockup:", color = Color(0xFF44474E), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ProfileAvatar(
                                        uri = avatarUri,
                                        presetIndex = avatarPresetIndex,
                                        size = 64.dp
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { avatarPickerLauncher.launch("image/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unggah Foto Profil", fontSize = 12.sp)
                                        }

                                        if (avatarUri != null) {
                                            TextButton(
                                                onClick = { avatarUri = null },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                                            ) {
                                                Text("Reset ke Preset", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                if (avatarUri == null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Rekomendasi Avatar Preset (Pilih):", color = Color(0xFF44474E), fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val presets = listOf("👨‍💻", "👩‍💼", "🐱", "🚀", "🕶️", "🦄")
                                        presets.forEachIndexed { index, label ->
                                            val isSelected = avatarPresetIndex == index
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color(0xFF0061A4) else Color(0xFFE2E8F0)
                                                    )
                                                    .clickable { avatarPresetIndex = index }
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = Color.White,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = label, fontSize = 18.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Text fields for input config
                                OutlinedTextField(
                                    value = profileName,
                                    onValueChange = { profileName = it },
                                    label = { Text("Nama Profil / Judul") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1C1B1F),
                                        unfocusedTextColor = Color(0xFF1C1B1F),
                                        focusedBorderColor = Color(0xFF0061A4),
                                        unfocusedBorderColor = Color(0xFFC4C6D0),
                                        focusedLabelColor = Color(0xFF0061A4),
                                        unfocusedLabelColor = Color(0xFF44474E)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (currentPlatform in listOf(
                                        SocialPlatform.INSTAGRAM_DM,
                                        SocialPlatform.INSTAGRAM_COMMENT,
                                        SocialPlatform.FACEBOOK_COMMENT,
                                        SocialPlatform.TIKTOK_COMMENT,
                                        SocialPlatform.TIKTOK_DM
                                    )
                                ) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text("Username Handle (@)") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF1C1B1F),
                                            unfocusedTextColor = Color(0xFF1C1B1F),
                                            focusedBorderColor = Color(0xFF0061A4),
                                            unfocusedBorderColor = Color(0xFFC4C6D0),
                                            focusedLabelColor = Color(0xFF0061A4),
                                            unfocusedLabelColor = Color(0xFF44474E)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                if (currentPlatform in listOf(
                                        SocialPlatform.WHATSAPP_CHAT,
                                        SocialPlatform.INSTAGRAM_DM,
                                        SocialPlatform.FACEBOOK_MESSENGER,
                                        SocialPlatform.TIKTOK_DM
                                    )
                                ) {
                                    OutlinedTextField(
                                        value = profileSubtitle,
                                        onValueChange = { profileSubtitle = it },
                                        label = { Text("Subtitle / Status (Online, dll)") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF1C1B1F),
                                            unfocusedTextColor = Color(0xFF1C1B1F),
                                            focusedBorderColor = Color(0xFF0061A4),
                                            unfocusedBorderColor = Color(0xFFC4C6D0),
                                            focusedLabelColor = Color(0xFF0061A4),
                                            unfocusedLabelColor = Color(0xFF44474E)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Toggle buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Centang Biru Verified:", color = Color(0xFF1C1B1F), fontSize = 14.sp)
                                    Switch(
                                        checked = isVerified,
                                        onCheckedChange = { isVerified = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0061A4))
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Preview Dark Mode (Tema Gelap):", color = Color(0xFF1C1B1F), fontSize = 14.sp)
                                    Switch(
                                        checked = isDarkPreview,
                                        onCheckedChange = { isDarkPreview = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0061A4))
                                    )
                                }
                            }
                        }

                        // WhatsApp Status & Social Posting specific caption card
                        if (currentPlatform in listOf(
                                SocialPlatform.WHATSAPP_STATUS,
                                SocialPlatform.INSTAGRAM_COMMENT,
                                SocialPlatform.FACEBOOK_COMMENT,
                                SocialPlatform.TIKTOK_COMMENT
                            )
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "📝 Isi Postingan / Caption Utama",
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = generalPostCaption,
                                        onValueChange = { generalPostCaption = it },
                                        label = { Text("Caption Post") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF1C1B1F),
                                            unfocusedTextColor = Color(0xFF1C1B1F),
                                            focusedBorderColor = Color(0xFF0061A4),
                                            unfocusedBorderColor = Color(0xFFC4C6D0),
                                            focusedLabelColor = Color(0xFF0061A4),
                                            unfocusedLabelColor = Color(0xFF44474E)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 4
                                    )

                                    if (currentPlatform == SocialPlatform.WHATSAPP_STATUS) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "🎨 Background Gradient Status WA:",
                                            color = Color(0xFF44474E),
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            for (i in statusBgGradients.indices) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(statusBgGradients[i])
                                                        .clickable { statusBgGradientIndex = i }
                                                        .border(
                                                            width = if (statusBgGradientIndex == i) 2.dp else 0.dp,
                                                            color = Color.White,
                                                            shape = CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Fast Navigation Tip
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💡", fontSize = 18.sp)
                                Text(
                                    text = "Atur isi pesan percakapan chat, centang dua biru, pengirim, dan komentar di tab berikutnya 'Edit Isi Konten'.",
                                    color = Color(0xFF44474E),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: DISCUSSIONS/CHAT/COMMENTS DETAILED EDITING
                    val isCommentMode = currentPlatform in listOf(
                        SocialPlatform.INSTAGRAM_COMMENT,
                        SocialPlatform.FACEBOOK_COMMENT,
                        SocialPlatform.TIKTOK_COMMENT
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isCommentMode) {
                            // CHAT MESSAGE EDITOR INTERFACE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(2.dp, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "💬 Atur Pesan Chat Balon",
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // List of current messages
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(chatMessages) { chat ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (chat.isSelf) Color(0xFFD8E2FF)
                                                        else Color(0xFFF1F5F9),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (chat.isSelf) Color(0xFFB1C5FF)
                                                        else Color(0xFFE1E3E8),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (chat.isSelf) Color(0xFF0061A4) else Color(0xFF64748B))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (chat.isSelf) "Diri Sendiri (Kanan)" else "Orang Lain (Kiri)",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(chat.time, color = Color(0xFF44474E), fontSize = 11.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(chat.text, color = Color(0xFF1C1B1F), fontSize = 13.sp)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        chatMessages = chatMessages.filter { it.id != chat.id }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus",
                                                        tint = Color.Red.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFE1E3E8), modifier = Modifier.padding(vertical = 12.dp))

                                    // Form to add a new Chat Balloon
                                    Text("Tambah Pesan Percakapan:", color = Color(0xFF1C1B1F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    var newMsgText by remember { mutableStateOf("") }
                                    var newMsgIsSelf by remember { mutableStateOf(true) }
                                    var newMsgTime by remember { mutableStateOf("10:45") }
                                    var newMsgStatus by remember { mutableStateOf("READ") } // for WA

                                    OutlinedTextField(
                                        value = newMsgText,
                                        onValueChange = { newMsgText = it },
                                        placeholder = { Text("Ketik pesan baru disini...", color = Color(0xFF94A3B8)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF1C1B1F),
                                            unfocusedTextColor = Color(0xFF1C1B1F),
                                            focusedBorderColor = Color(0xFF0061A4),
                                            unfocusedBorderColor = Color(0xFFC4C6D0)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Sender Segmented Control
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (newMsgIsSelf) Color(0xFF0061A4) else Color.Transparent)
                                                    .clickable { newMsgIsSelf = true }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Kanan (Self)", color = if (newMsgIsSelf) Color.White else Color(0xFF44474E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (!newMsgIsSelf) Color(0xFF0061A4) else Color.Transparent)
                                                    .clickable { newMsgIsSelf = false }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Kiri (Lawan)", color = if (!newMsgIsSelf) Color.White else Color(0xFF44474E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Time Input
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("Waktu:", color = Color(0xFF44474E), fontSize = 11.sp)
                                            Box(
                                                modifier = Modifier
                                                    .width(70.dp)
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                BasicTextFieldInline(
                                                    value = newMsgTime,
                                                    onValueChange = { newMsgTime = it }
                                                )
                                            }
                                        }
                                    }

                                    if (newMsgIsSelf && currentPlatform == SocialPlatform.WHATSAPP_CHAT) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("Indikator Centang WA:", color = Color(0xFF44474E), fontSize = 11.sp)
                                            listOf("SENT" to "✓", "DELIVERED" to "✓✓ G", "READ" to "✓✓ B").forEach { (status, label) ->
                                                val isSelected = newMsgStatus == status
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) Color(0xFF0061A4) else Color(0xFFE2E8F0))
                                                        .clickable { newMsgStatus = status }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(text = label, color = if (isSelected) Color.White else Color(0xFF44474E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (newMsgText.isNotBlank()) {
                                                chatMessages = chatMessages + ChatMessage(
                                                    isSelf = newMsgIsSelf,
                                                    text = newMsgText,
                                                    time = newMsgTime,
                                                    status = newMsgStatus
                                                )
                                                newMsgText = ""
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddComment, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tambahkan Balon Chat")
                                    }
                                }
                            }
                        } else {
                            // COMMENTS LIST EDITOR INTERFACE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE1E3E8)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(2.dp, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "💬 Atur Daftar Komentar Pembaca",
                                        color = Color(0xFF1C1B1F),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Display comments
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(comments) { comment ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0xFFE1E3E8), RoundedCornerShape(8.dp))
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("@${comment.username}", color = Color(0xFF1C1B1F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        if (comment.isVerified) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("✓", color = Color(0xFF0061A4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        if (comment.isCreator) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(3.dp))
                                                                    .background(Color(0xFFFE2C55))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("Kreator", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(comment.time, color = Color(0xFF44474E), fontSize = 11.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(comment.text, color = Color(0xFF1C1B1F), fontSize = 13.sp)
                                                    Text("❤️ ${comment.likesCount} suka", color = Color(0xFF44474E), fontSize = 11.sp)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        comments = comments.filter { it.id != comment.id }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus",
                                                        tint = Color.Red.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFE1E3E8), modifier = Modifier.padding(vertical = 12.dp))

                                    // Comment Input Form
                                    Text("Tambah Komentar Kepuasan Pelanggan:", color = Color(0xFF1C1B1F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    var newCommUser by remember { mutableStateOf("") }
                                    var newCommText by remember { mutableStateOf("") }
                                    var newCommLikes by remember { mutableStateOf("12") }
                                    var newCommTime by remember { mutableStateOf("5m") }
                                    var newCommVerified by remember { mutableStateOf(false) }
                                    var newCommCreator by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newCommUser,
                                            onValueChange = { newCommUser = it },
                                            placeholder = { Text("Username", color = Color(0xFF94A3B8)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF1C1B1F),
                                                unfocusedTextColor = Color(0xFF1C1B1F),
                                                focusedBorderColor = Color(0xFF0061A4),
                                                unfocusedBorderColor = Color(0xFFC4C6D0)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = newCommTime,
                                            onValueChange = { newCommTime = it },
                                            placeholder = { Text("Waktu", color = Color(0xFF94A3B8)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF1C1B1F),
                                                unfocusedTextColor = Color(0xFF1C1B1F),
                                                focusedBorderColor = Color(0xFF0061A4),
                                                unfocusedBorderColor = Color(0xFFC4C6D0)
                                            ),
                                            modifier = Modifier.width(80.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = newCommText,
                                        onValueChange = { newCommText = it },
                                        placeholder = { Text("Tulis review komentar positif...", color = Color(0xFF94A3B8)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF1C1B1F),
                                            unfocusedTextColor = Color(0xFF1C1B1F),
                                            focusedBorderColor = Color(0xFF0061A4),
                                            unfocusedBorderColor = Color(0xFFC4C6D0)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = newCommVerified,
                                                onCheckedChange = { newCommVerified = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0061A4))
                                            )
                                            Text("Verified?", color = Color(0xFF1C1B1F), fontSize = 11.sp)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = newCommCreator,
                                                onCheckedChange = { newCommCreator = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFE2C55))
                                            )
                                            Text("Kreator?", color = Color(0xFF1C1B1F), fontSize = 11.sp)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Likes:", color = Color(0xFF44474E), fontSize = 11.sp)
                                            Box(
                                                modifier = Modifier
                                                    .width(50.dp)
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                BasicTextFieldInline(
                                                    value = newCommLikes,
                                                    onValueChange = { newCommLikes = it }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (newCommUser.isNotBlank() && newCommText.isNotBlank()) {
                                                comments = comments + SocialComment(
                                                    username = newCommUser,
                                                    text = newCommText,
                                                    time = newCommTime,
                                                    likesCount = newCommLikes,
                                                    isVerified = newCommVerified,
                                                    isCreator = newCommCreator,
                                                    avatarPreset = (0..5).random()
                                                )
                                                newCommUser = ""
                                                newCommText = ""
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AddComment, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tambahkan Komentar")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: STUDIO LIVE PREVIEW & EXPORT ACTIONS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "📸 Studio Pembuat Gambar Mockup HD",
                            color = Color(0xFF1C1B1F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Smartphone Mockup Canvas Frame (Scrollable to prevent offscreen cropping on smaller screens)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(360.dp)
                                    .height(610.dp)
                                    .padding(vertical = 8.dp)
                                    .shadow(12.dp, RoundedCornerShape(24.dp))
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(4.dp, Color(0xFFCBD5E1), RoundedCornerShape(24.dp))
                                    .background(if (isDarkPreview) Color(0xFF121212) else Color(0xFFFAFAFA))
                            ) {
                                // REAL CONTAINER EXPORTED VIA ANDROIDVIEW TO GUARANTEE BITMAP EXPORT FIDELITY
                                AndroidView(
                                    factory = { ctx ->
                                        ComposeView(ctx).apply {
                                            setContent {
                                                HighFidelityCanvas(
                                                    platform = currentPlatform,
                                                    profileName = profileName,
                                                    username = username,
                                                    subtitle = profileSubtitle,
                                                    isVerified = isVerified,
                                                    isOnline = isOnline,
                                                    isDark = isDarkPreview,
                                                    avatarUri = avatarUri,
                                                    avatarPreset = avatarPresetIndex,
                                                    chatMessages = chatMessages,
                                                    comments = comments,
                                                    postCaption = generalPostCaption,
                                                    waGradient = statusBgGradients[statusBgGradientIndex]
                                                )
                                            }
                                            composeViewReference = this
                                        }
                                    },
                                    update = { view ->
                                        view.setContent {
                                            HighFidelityCanvas(
                                                platform = currentPlatform,
                                                profileName = profileName,
                                                username = username,
                                                subtitle = profileSubtitle,
                                                isVerified = isVerified,
                                                isOnline = isOnline,
                                                isDark = isDarkPreview,
                                                avatarUri = avatarUri,
                                                avatarPreset = avatarPresetIndex,
                                                chatMessages = chatMessages,
                                                comments = comments,
                                                postCaption = generalPostCaption,
                                                waGradient = statusBgGradients[statusBgGradientIndex]
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // HIGH DENSITY EXPORT ACTION MODULE
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val bitmap = composeViewReference?.drawToBitmap()
                                    if (bitmap != null) {
                                        val uri = saveBitmapToGallery(context, bitmap, "SocialProof_${currentPlatform.name}")
                                        if (uri != null) {
                                            Toast.makeText(context, "✅ Mockup berhasil disimpan ke Galeri Pictures/SocialProofCreator!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "⚠️ Gagal membuat file gambar di gallery", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "⚠️ Canvas preview tidak siap untuk diekspor", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("submit_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unduh Gambar (HD)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val bitmap = composeViewReference?.drawToBitmap()
                                    if (bitmap != null) {
                                        shareMockupDirectly(context, bitmap, "SocialProofShare")
                                    } else {
                                        Toast.makeText(context, "⚠️ Gagal memproses gambar untuk dibagikan", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color(0xFF1C1B1F)),
                                border = BorderStroke(1.dp, Color(0xFFC4C6D0)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF1C1B1F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bagikan Direct", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1C1B1F))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// HIGH FIDELITY CANVAS COMPONENT
// ==========================================

@Composable
fun HighFidelityCanvas(
    platform: SocialPlatform,
    profileName: String,
    username: String,
    subtitle: String,
    isVerified: Boolean,
    isOnline: Boolean,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    chatMessages: List<ChatMessage>,
    comments: List<SocialComment>,
    postCaption: String,
    waGradient: Brush
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF))
    ) {
        when (platform) {
            SocialPlatform.WHATSAPP_CHAT -> {
                WhatsAppChatMockup(
                    profileName = profileName,
                    subtitle = subtitle,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    chatMessages = chatMessages
                )
            }
            SocialPlatform.WHATSAPP_STATUS -> {
                WhatsAppStatusMockup(
                    profileName = profileName,
                    caption = postCaption,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    gradient = waGradient
                )
            }
            SocialPlatform.INSTAGRAM_DM -> {
                InstagramDMMockup(
                    profileName = profileName,
                    username = username,
                    subtitle = subtitle,
                    isVerified = isVerified,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    chatMessages = chatMessages
                )
            }
            SocialPlatform.INSTAGRAM_COMMENT -> {
                InstagramCommentMockup(
                    username = username,
                    profileName = profileName,
                    caption = postCaption,
                    isVerified = isVerified,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    comments = comments
                )
            }
            SocialPlatform.FACEBOOK_MESSENGER -> {
                FacebookMessengerMockup(
                    profileName = profileName,
                    subtitle = subtitle,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    chatMessages = chatMessages
                )
            }
            SocialPlatform.FACEBOOK_COMMENT -> {
                FacebookCommentMockup(
                    profileName = profileName,
                    username = username,
                    caption = postCaption,
                    isVerified = isVerified,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    comments = comments
                )
            }
            SocialPlatform.TIKTOK_COMMENT -> {
                TikTokCommentMockup(
                    username = username,
                    caption = postCaption,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    comments = comments
                )
            }
            SocialPlatform.TIKTOK_DM -> {
                TikTokDMMockup(
                    profileName = profileName,
                    username = username,
                    subtitle = subtitle,
                    isVerified = isVerified,
                    isDark = isDark,
                    avatarUri = avatarUri,
                    avatarPreset = avatarPreset,
                    chatMessages = chatMessages
                )
            }
        }
    }
}

// ==========================================
// 1. WHATSAPP CHAT PREVIEW MODUL
// ==========================================
@Composable
fun WhatsAppChatMockup(
    profileName: String,
    subtitle: String,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    chatMessages: List<ChatMessage>
) {
    val waHeaderBg = if (isDark) Color(0xFF1F2C34) else Color(0xFF008069)
    val waChatBg = if (isDark) Color(0xFF0B141A) else Color(0xFFE5DDD5)
    val textColorHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFF8696A0) else Color(0xFF667781)

    Column(modifier = Modifier.fillMaxSize()) {
        // WA AppBar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(waHeaderBg)
                .padding(vertical = 12.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(4.dp))

            // Avatar & Profile
            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color.White)
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
            }
        }

        // Chat Screen area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(waChatBg)
                .padding(12.dp)
        ) {
            // Background Chat Bubble Pattern overlay simulated with solid back
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Add center date stamp
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(
                            if (isDark) Color(0xFF1F2C34).copy(alpha = 0.8f) else Color(0xFFFFFFFF),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "HARI INI",
                        color = textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                chatMessages.forEach { chat ->
                    val bubbleBg = if (chat.isSelf) {
                        if (isDark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
                    } else {
                        if (isDark) Color(0xFF1F2C34) else Color(0xFFFFFFFF)
                    }

                    val alignment = if (chat.isSelf) Alignment.End else Alignment.Start
                    val bubbleShape = if (chat.isSelf) {
                        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
                    } else {
                        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(alignment),
                        horizontalAlignment = if (chat.isSelf) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .background(bubbleBg, bubbleShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = chat.text,
                                    color = textColorHero,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = chat.time,
                                        color = textMuted,
                                        fontSize = 10.sp
                                    )
                                    if (chat.isSelf) {
                                        when (chat.status) {
                                            "SENT" -> {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = textMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            "DELIVERED" -> {
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = null,
                                                    tint = textMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            "READ" -> {
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = null,
                                                    tint = Color(0xFF53BDEB),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mock Bottom Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color(0xFF1F2C34) else Color(0xFFF0F2F5))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isDark) Color(0xFF2A3942) else Color(0xFFFFFFFF),
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.SentimentSatisfied, contentDescription = null, tint = textMuted)
                Text(
                    text = "Ketik pesan",
                    color = textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = textMuted)
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = textMuted)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isDark) Color(0xFF00A884) else Color(0xFF00A884), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ==========================================
// 2. WHATSAPP STATUS PREVIEW MODUL
// ==========================================
@Composable
fun WhatsAppStatusMockup(
    profileName: String,
    caption: String,
    avatarUri: String?,
    avatarPreset: Int,
    gradient: Brush
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(16.dp)
    ) {
        // Status top progress bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .height(3.dp)
                    .background(Color.White)
            )
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }

        // Close/Exit X top-right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = profileName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Baru saja aktif",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Caption Box (WhatsApp Style text centered)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = caption,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
        }

        // Swift bottom navigation
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White)
            Text(text = "BALAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

// ==========================================
// 3. INSTAGRAM DM PREVIEW MODUL
// ==========================================
@Composable
fun InstagramDMMockup(
    profileName: String,
    username: String,
    subtitle: String,
    isVerified: Boolean,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    chatMessages: List<ChatMessage>
) {
    val igBg = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFFA8A8A8) else Color(0xFF737373)
    val dividerColor = if (isDark) Color(0xFF262626) else Color(0xFFDBDBDB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(igBg)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(igBg)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = textHero)
            Spacer(modifier = Modifier.width(10.dp))

            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 32.dp)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profileName,
                        color = textHero,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF3897F0),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = "@$username • $subtitle",
                    color = textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Call, contentDescription = null, tint = textHero)
                Icon(Icons.Default.VideoCall, contentDescription = null, tint = textHero)
            }
        }

        Divider(color = dividerColor)

        // DM Body Chat bubble list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Profile center header card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 64.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profileName, color = textHero, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF3897F0), modifier = Modifier.size(15.dp))
                    }
                }
                Text(text = "@$username • Instagram", color = textMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(if (isDark) Color(0xFF262626) else Color(0xFFEFEFEF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Lihat Profil", color = textHero, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            chatMessages.forEach { chat ->
                val bubbleBg = if (chat.isSelf) {
                    Color(0xFF3797EF) // IG Standard Blue gradient substitute
                } else {
                    if (isDark) Color(0xFF262626) else Color(0xFFEFEFEF)
                }
                val bubbleTextClr = if (chat.isSelf) Color.White else textHero
                val bubbleAlignment = if (chat.isSelf) Alignment.End else Alignment.Start

                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .background(bubbleBg, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .align(bubbleAlignment)
                ) {
                    Text(
                        text = chat.text,
                        color = bubbleTextClr,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Mock DM Footer Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF363636) else Color(0xFFDBDBDB),
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = textHero)
                Text(text = "Kirim pesan...", color = textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Mic, contentDescription = null, tint = textHero)
                Icon(Icons.Default.Image, contentDescription = null, tint = textHero)
            }
        }
    }
}

// ==========================================
// 4. INSTAGRAM COMMENT PREVIEW MODUL
// ==========================================
@Composable
fun InstagramCommentMockup(
    username: String,
    profileName: String,
    caption: String,
    isVerified: Boolean,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    comments: List<SocialComment>
) {
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFFA8A8A8) else Color(0xFF737373)
    val dividerColor = if (isDark) Color(0xFF262626) else Color(0xFFDBDBDB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Comment Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = textHero)
            Text("Komentar", color = textHero, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Icon(Icons.Default.Send, contentDescription = null, tint = textHero)
        }

        Divider(color = dividerColor)

        // Post description author main comment
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = username, color = textHero, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF3897F0), modifier = Modifier.size(13.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1h", color = textMuted, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = caption, color = textHero, fontSize = 13.sp)
            }
        }

        Divider(color = dividerColor.copy(alpha = 0.5f))

        // Comments scrolling simulator list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            comments.forEach { comment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ProfileAvatar(uri = null, presetIndex = comment.avatarPreset, size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = comment.username, color = textHero, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (comment.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF3897F0), modifier = Modifier.size(12.dp))
                            }
                            if (comment.isCreator) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFFE2C55))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("Creator", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(comment.time, color = textMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = comment.text, color = textHero, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Balas", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${comment.likesCount} suka", color = textMuted, fontSize = 11.sp)
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Divider(color = dividerColor)

        // Bottom text comment box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(bg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 32.dp)
            Text(text = "Tambahkan komentar untuk @$username...", color = textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("Kirim", color = Color(0xFF3897F0), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ==========================================
// 5. FACEBOOK MESSENGER PREVIEW MODUL
// ==========================================
@Composable
fun FacebookMessengerMockup(
    profileName: String,
    subtitle: String,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    chatMessages: List<ChatMessage>
) {
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFF8A8D91) else Color(0xFF65676B)
    val selfBubbleBg = Color(0xFF0084FF)
    val otherBubbleBg = if (isDark) Color(0xFF242526) else Color(0xFFE4E6EB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // FB AppBar header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = selfBubbleBg)
            Spacer(modifier = Modifier.width(8.dp))

            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName,
                    color = textHero,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF31A24C), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = subtitle, color = textMuted, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = selfBubbleBg)
                Icon(Icons.Default.VideoCall, contentDescription = null, tint = selfBubbleBg)
                Icon(Icons.Default.Info, contentDescription = null, tint = selfBubbleBg)
            }
        }

        Divider(color = otherBubbleBg, thickness = 0.5.dp)

        // Messenger body chats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            chatMessages.forEach { chat ->
                val bubbleBg = if (chat.isSelf) selfBubbleBg else otherBubbleBg
                val itemTextClr = if (chat.isSelf) Color.White else textHero
                val alignment = if (chat.isSelf) Alignment.End else Alignment.Start

                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .background(bubbleBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .align(alignment)
                ) {
                    Text(
                        text = chat.text,
                        color = itemTextClr,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Action Toolbar footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AddCircle, contentDescription = null, tint = selfBubbleBg)
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = selfBubbleBg)
            Icon(Icons.Default.Image, contentDescription = null, tint = selfBubbleBg)
            Icon(Icons.Default.Mic, contentDescription = null, tint = selfBubbleBg)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(otherBubbleBg, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Aa", color = textMuted, fontSize = 14.sp)
            }
            Icon(Icons.Default.ThumbUp, contentDescription = null, tint = selfBubbleBg)
        }
    }
}

// ==========================================
// 6. FACEBOOK COMMENT PREVIEW MODUL
// ==========================================
@Composable
fun FacebookCommentMockup(
    profileName: String,
    username: String,
    caption: String,
    isVerified: Boolean,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    comments: List<SocialComment>
) {
    val bg = if (isDark) Color(0xFF18191A) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFFB0B3B8) else Color(0xFF65676B)
    val cardBg = if (isDark) Color(0xFF242526) else Color(0xFFF0F2F5)
    val dividerColor = if (isDark) Color(0xFF3E4042) else Color(0xFFE5E5E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Facebook Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 42.dp)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profileName, color = textHero, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(14.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Baru saja", color = textMuted, fontSize = 11.sp)
                    Text("•", color = textMuted, fontSize = 11.sp)
                    Icon(Icons.Default.Public, contentDescription = null, tint = textMuted, modifier = Modifier.size(12.dp))
                }
            }

            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = textMuted)
        }

        // Post body text
        Text(
            text = caption,
            color = textHero,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = dividerColor)

        // Likes count bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFF1877F2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagaskara dan 242 lainnya", color = textMuted, fontSize = 11.sp)
            }
            Text("14 Komentar", color = textMuted, fontSize = 11.sp)
        }

        Divider(color = dividerColor)

        // Comment thread loop
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            comments.forEach { comment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ProfileAvatar(uri = null, presetIndex = comment.avatarPreset, size = 32.dp)
                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Box(
                            modifier = Modifier
                                .background(cardBg, RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = comment.username, color = textHero, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (comment.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = comment.text, color = textHero, fontSize = 12.sp)
                            }
                        }

                        // Reply footer metadata
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 10.dp)
                        ) {
                            Text(comment.time, color = textMuted, fontSize = 10.sp)
                            Text("Suka", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Balas", color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. TIKTOK COMMENT PREVIEW MODUL
// ==========================================
@Composable
fun TikTokCommentMockup(
    username: String,
    caption: String,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    comments: List<SocialComment>
) {
    val bg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFF8A8B90) else Color(0xFF65666B)
    val bottomSheetBg = if (isDark) Color(0xFF161823) else Color(0xFFF8F8F8)
    val dividerColor = if (isDark) Color(0xFF2F313E) else Color(0xFFE5E5E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Simulated full screen backdrop overlay, with TikTok comment bottom sheet taking up bottom 80%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.15f)
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.85f)
                .background(bottomSheetBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            // Sheet bar handle and close icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "34 Komentar",
                    color = textHero,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = textHero,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(18.dp)
                )
            }

            Divider(color = dividerColor)

            // Comments lists
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                comments.forEach { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        ProfileAvatar(uri = null, presetIndex = comment.avatarPreset, size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = comment.username, color = textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (comment.isVerified) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF25F4EE), modifier = Modifier.size(11.dp))
                                }
                                if (comment.isCreator) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFFFE2C55))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("Kreator", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = comment.text, color = textHero, fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(comment.time, color = textMuted, fontSize = 11.sp)
                                Text("Balas", color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = comment.likesCount, color = textMuted, fontSize = 9.sp)
                        }
                    }
                }
            }

            Divider(color = dividerColor)

            // Message box input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatar(uri = avatarUri, presetIndex = avatarPreset, size = 32.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isDark) Color(0xFF2F313E) else Color(0xFFEFEFEF), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Tambahkan komentar...", color = textMuted, fontSize = 12.sp)
                }
                Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = textHero)
                Icon(Icons.Default.SentimentSatisfied, contentDescription = null, tint = textHero)
            }
        }
    }
}

// ==========================================
// 8. TIKTOK DM PREVIEW MODUL
// ==========================================
@Composable
fun TikTokDMMockup(
    profileName: String,
    username: String,
    subtitle: String,
    isVerified: Boolean,
    isDark: Boolean,
    avatarUri: String?,
    avatarPreset: Int,
    chatMessages: List<ChatMessage>
) {
    val bg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val textHero = if (isDark) Color.White else Color.Black
    val textMuted = if (isDark) Color(0xFF86878B) else Color(0xFF767676)
    val selfBubbleBg = Color(0xFF25F4EE) // Cyan
    val otherBubbleBg = if (isDark) Color(0xFF2F313E) else Color(0xFFE5E5E5)
    val dmsDivider = if (isDark) Color(0xFF2F313E) else Color(0xFFE5E5E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // DM header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = textHero)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profileName, color = textHero, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF25F4EE), modifier = Modifier.size(13.dp))
                    }
                }
                Text(text = "@$username • $subtitle", color = textMuted, fontSize = 11.sp)
            }

            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = textHero)
        }

        Divider(color = dmsDivider)

        // DMs chats
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            chatMessages.forEach { chat ->
                val bubbleBg = if (chat.isSelf) selfBubbleBg else otherBubbleBg
                val itemTextClr = if (chat.isSelf) Color.Black else textHero
                val alignment = if (chat.isSelf) Alignment.End else Alignment.Start

                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .background(bubbleBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(alignment)
                ) {
                    Text(
                        text = chat.text,
                        color = itemTextClr,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Divider(color = dmsDivider)

        // Footer message bar mockup
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = textHero)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(otherBubbleBg, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Kirim pesan...", color = textMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.SentimentSatisfied, contentDescription = null, tint = textHero)
        }
    }
}

// ==========================================
// CENTRAL REUSABLE CORE HELPER UTILITIES
// ==========================================

@Composable
fun ProfileAvatar(
    uri: String?,
    presetIndex: Int,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        Pair("👨‍💻", Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF00BCD4)))), // Tech Blue
        Pair("👩‍💼", Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFF9C27B0)))), // Prof Pink
        Pair("🐱", Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722)))),   // Cute Orange
        Pair("🚀", Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)))),   // Growth Green
        Pair("🕶️", Brush.linearGradient(listOf(Color(0xFF607D8B), Color(0xFF9E9E9E)))),   // Slate Cool
        Pair("🦄", Brush.linearGradient(listOf(Color(0xFFFFC107), Color(0xFFFF9800))))    // Art Yellow
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNullOrEmpty()) {
            AsyncImage(
                model = uri,
                contentDescription = "Avatar Profil",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val preset = presets.getOrNull(presetIndex) ?: presets[0]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(preset.second),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.first,
                    fontSize = (size.value * 0.45f).sp
                )
            }
        }
    }
}

@Composable
fun BasicTextFieldInline(
    value: String,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

// ==========================================
// EXPORT & SAVE HELPER PROCEDURES
// ==========================================

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SocialProofCreator")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        try {
            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return uri
}

fun shareMockupDirectly(context: Context, bitmap: Bitmap, name: String) {
    val tempFile = java.io.File(context.cacheDir, "$name.png")
    try {
        java.io.FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan Mockup HD"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
