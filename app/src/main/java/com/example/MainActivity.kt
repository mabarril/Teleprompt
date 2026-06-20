package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Song
import com.example.speech.SyncStatus
import com.example.ui.LyricsViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                // Ensure overall app background is Solid black/stage styling
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LyricsStageApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsStageApp() {
    val context = LocalContext.current
    val viewModel: LyricsViewModel = viewModel()

    // State bindings
    val songs by viewModel.songs.collectAsState()
    val selectedSong by viewModel.selectedSong.collectAsState()
    val currentLineIndex by viewModel.currentLineIndex.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val speechErrorMessage by viewModel.speechErrorMessage.collectAsState()
    val partialSpokenText by viewModel.partialSpokenText.collectAsState()
    val finalSpokenText by viewModel.finalSpokenText.collectAsState()

    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val matchSensitivity by viewModel.matchSensitivity.collectAsState()
    val isAutoplayActive by viewModel.isAutoplayActive.collectAsState()
    val autoplaySeconds by viewModel.autoplaySecondsPerLine.collectAsState()

    // Dialog binders
    val showAddSongDialog by viewModel.showAddSongDialog.collectAsState()
    val showEditSongDialog by viewModel.showEditSongDialog.collectAsState()

    // MIC permission launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPrompterOnMobile by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var isFullscreenStageMode by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted) {
                Toast.makeText(context, "Microfone liberado! Pronto para cantar.", Toast.LENGTH_SHORT).show()
            } else {
                // Check if it's permanently denied
                val activity = context as? android.app.Activity
                if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    showPermissionDialog = true
                } else {
                    Toast.makeText(context, "Permissão negada. A sincronização automática não funcionará.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // Trigger initial permission check or prompt
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissão Necessária") },
            text = { Text("Você negou o acesso ao microfone. Para usar o sincronismo vocal, vá nas Configurações -> Apps -> Lyrics Stage Sync -> Permissões e habilite o acesso ao microfone.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    // Optional: Open Settings?
                }) {
                    Text("Entendido")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        // Main Tablet Screen Layout: Horizontal split
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(StageBg) // Very deep stage space
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 720.dp
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isFullScreenPrompter = selectedSong != null && (isLandscape || isFullscreenStageMode)

                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT COLUMN: Song Library & Settings Controls
                    if (!isFullScreenPrompter && (isTablet || !showPrompterOnMobile)) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .then(
                                    if (isTablet) Modifier.width(360.dp) else Modifier.weight(1f)
                                )
                                .background(StageCardBg)
                                .border(1.dp, StageGridLine)
                        ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Lyrics Stage",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${songs.size} músicas",
                                fontSize = 13.sp,
                                color = StageTextMuted
                            )
                        }

                        IconButton(
                            onClick = { viewModel.openAddSongDialog() },
                            modifier = Modifier
                                .testTag("add_song_button")
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Adicionar nova música",
                                tint = Color.Black
                            )
                        }
                    }

                    // Tab Selector for Mobile (Repertório vs Ajustes)
                    if (!isTablet) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(Color(0xFF0F1118), RoundedCornerShape(10.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 0 }
                                    .background(
                                        if (selectedTab == 0) Color(0xFF1E2436) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Músicas",
                                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else StageTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Músicas",
                                        color = if (selectedTab == 0) Color.White else StageTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = 1 }
                                    .background(
                                        if (selectedTab == 1) Color(0xFF1E2436) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Ajustes",
                                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else StageTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ajustes",
                                        color = if (selectedTab == 1) Color.White else StageTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (isTablet || selectedTab == 0) {
                        // Search field / Filter State
                        var searchQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .testTag("search_lyrics_input"),
                            placeholder = { Text("Pesquisar música por título/artista", color = StageTextMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = StageTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = StageGridLine,
                                focusedContainerColor = Color(0xFF1B1E29),
                                unfocusedContainerColor = Color(0xFF0F1118)
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Repertoire Library List
                        val filteredSongs = songs.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.artist.contains(searchQuery, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            itemsIndexed(filteredSongs) { _, song ->
                                val isSelected = selectedSong?.id == song.id
                                SongGridItem(
                                    song = song,
                                    isSelected = isSelected,
                                    onClick = {
                                        viewModel.selectSong(song)
                                        showPrompterOnMobile = true
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (filteredSongs.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nenhuma música encontrada.",
                                            color = StageTextMuted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isTablet || selectedTab == 1) {
                        if (isTablet) {
                            Divider(color = StageGridLine, thickness = 1.dp)
                        }

                        // BOTTOM OF LEFT PANEL: Microphones & Live Settings panel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isTablet) Modifier.wrapContentHeight()
                                    else Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                )
                                .background(StageBg)
                                .padding(16.dp)
                        ) {
                            // Section: Microphone Input
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                                contentDescription = "Estado do microfone",
                                                tint = if (isListening) StageActiveGreen else StageTextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Acompanhamento de Voz",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Switch(
                                            checked = isListening,
                                            onCheckedChange = {
                                                if (!hasMicPermission) {
                                                    val activity = context as? android.app.Activity
                                                    if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                                                        // Already denied perma, show helping dialog
                                                        showPermissionDialog = true
                                                    } else {
                                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                } else {
                                                    viewModel.toggleVocalSync()
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = StageActiveGreen,
                                                checkedTrackColor = Color(0xFF0F3A15)
                                            ),
                                            modifier = Modifier.testTag("microphone_sync_switch")
                                        )
                                    }

                                    if (isListening) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Captando:",
                                                fontSize = 11.sp,
                                                color = StageTextMuted
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val activeSpeech = when {
                                                partialSpokenText.isNotBlank() -> partialSpokenText
                                                finalSpokenText.isNotBlank() -> finalSpokenText
                                                else -> "Silêncio vocal..."
                                            }
                                            Text(
                                                text = activeSpeech,
                                                fontSize = 12.sp,
                                                color = StageActiveGreen,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        // dB Visualizer Bar indicator
                                        LinearProgressIndicator(
                                            progress = rmsDb,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(CircleShape),
                                            color = StageActiveGreen,
                                            trackColor = Color(0xFF232B25)
                                        )
                                    }

                                    if (speechErrorMessage != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = speechErrorMessage ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Controls: Font Size control
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tamanho de Letra (Palco)",
                                    fontSize = 13.sp,
                                    color = Color.White
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledIconButton(
                                        onClick = { viewModel.updateFontSize(-4f) },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1D2233))
                                    ) {
                                        Text("A-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${fontSizeSp.toInt()}sp",
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledIconButton(
                                        onClick = { viewModel.updateFontSize(4f) },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1D2233))
                                    ) {
                                        Text("A+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Sensitivity Slider Setup
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Sensibilidade I.A. Sincronismo",
                                        fontSize = 11.sp,
                                        color = StageTextMuted
                                    )
                                    Text(
                                        text = "${((1.0f - matchSensitivity) * 100f).toInt()}%",
                                        fontSize = 11.sp,
                                        color = StageActiveGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = matchSensitivity,
                                    onValueChange = { viewModel.updateSensitivity(it) },
                                    valueRange = 0.15f..0.70f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = StageActiveGreen,
                                        activeTrackColor = StageActiveGreen,
                                        inactiveTrackColor = StageGridLine
                                    )
                                )
                            }

                            // Simulation control: Autoplay Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Autoplay / Teste Rápido",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Velocidade: $autoplaySeconds seg/verso",
                                        fontSize = 10.sp,
                                        color = StageTextMuted
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.updateAutoplaySpeed(autoplaySeconds - 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Diminuir tempo", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.updateAutoplaySpeed(autoplaySeconds + 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Aumentar tempo", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.toggleAutoplay() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isAutoplayActive) StageNextYellow else Color(0xFF1E2130),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isAutoplayActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Autoplay",
                                            tint = if (isAutoplayActive) Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // RIGHT COLUMN: Prompter (Stage Screen)
                if (isFullScreenPrompter || isTablet || showPrompterOnMobile) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black)
                    ) {
                    if (selectedSong != null) {
                        val song = selectedSong!!
                        val lines = song.getLines()

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar inside Prompter Space
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                                    .padding(
                                        horizontal = if (isFullScreenPrompter) 16.dp else 24.dp,
                                        vertical = if (isFullScreenPrompter) 8.dp else 16.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (!isTablet || isFullScreenPrompter) {
                                    IconButton(
                                        onClick = { 
                                            isFullscreenStageMode = false
                                            showPrompterOnMobile = false 
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Voltar",
                                            tint = Color.White
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontSize = if (isFullScreenPrompter) 18.sp else 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "por ${song.artist}",
                                        fontSize = if (isFullScreenPrompter) 12.sp else 14.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Sync Badges & Buttons
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Status Badge
                                    SyncStatusBadge(isListening = isListening, syncStatus = syncStatus)

                                    // Fullscreen/Stage Mode Toggle Button
                                    IconButton(
                                        onClick = { isFullscreenStageMode = !isFullscreenStageMode },
                                        modifier = Modifier.background(
                                            if (isFullscreenStageMode) Color(0xFF333333) else Color(0xFF1E1E1E),
                                            CircleShape
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreenStageMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = "Alternar Modo Palco",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Quick settings switch
                                    IconButton(
                                        onClick = { showQuickSettings = !showQuickSettings },
                                        modifier = Modifier.background(
                                            if (showQuickSettings) MaterialTheme.colorScheme.primary else Color(0xFF1E1E1E),
                                            CircleShape
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Ajustes Rápidos",
                                            tint = if (showQuickSettings) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (!isFullScreenPrompter) {
                                        FilledTonalButton(
                                            onClick = { viewModel.openEditSongDialog() },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E1E1E))
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Letra", color = Color.White, fontSize = 12.sp)
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteCurrentSong() },
                                            modifier = Modifier.background(Color(0xFF291515), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Dynamic Live Microphone Interactive Sine Wave Visualizer
                            if (isListening) {
                                LiveSineWaveCanvas(rmsDb = rmsDb, isFullScreen = isFullScreenPrompter)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isFullScreenPrompter) 1.dp else 4.dp)
                                        .background(Color.Transparent)
                                )
                            }

                            // Lyrics Teleprompter Container (Keeping current song line at the top)
                            val listState = rememberLazyListState()

                            // Top alignment scroll trigger
                            LaunchedEffect(currentLineIndex) {
                                if (currentLineIndex >= 0 && lines.isNotEmpty()) {
                                    listState.animateScrollToItem(currentLineIndex)
                                }
                            }

                            val prompterTopPadding = 12.dp
                            val prompterBottomPadding = 500.dp

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isFullScreenPrompter) 12.dp else 30.dp),
                                contentPadding = PaddingValues(top = prompterTopPadding, bottom = prompterBottomPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isFullScreenPrompter) 30.dp else 48.dp)
                            ) {
                                itemsIndexed(lines) { index, lyricLine ->
                                    val isCurrent = index == currentLineIndex
                                    val isNext = index == currentLineIndex + 1
                                    val isPast = index < currentLineIndex

                                    PrompterLyricRow(
                                        text = lyricLine,
                                        isCurrent = isCurrent,
                                        isNext = isNext,
                                        isPast = isPast,
                                        fontSizeSp = fontSizeSp,
                                        isFullScreen = isFullScreenPrompter,
                                        onClick = { viewModel.setCurrentLineIndex(index) }
                                    )
                                }
                            }

                            // Quick settings overlay inside prompter
                            AnimatedVisibility(
                                visible = showQuickSettings,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
                                    border = BorderStroke(1.dp, StageGridLine),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Column 1: Mic control (Acompanhamento de Voz)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                                    contentDescription = null,
                                                    tint = if (isListening) StageActiveGreen else StageTextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Voz", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Switch(
                                                checked = isListening,
                                                onCheckedChange = {
                                                    if (!hasMicPermission) {
                                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        viewModel.toggleVocalSync()
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = StageActiveGreen,
                                                    checkedTrackColor = Color(0xFF0F3A15)
                                                ),
                                                modifier = Modifier.testTag("quick_mic_switch")
                                            )
                                        }

                                        // Column 2: Font Size control (Tamanho de Letra)
                                        Column(
                                            modifier = Modifier.weight(1.2f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Tamanho da Letra", fontSize = 11.sp, color = StageTextMuted, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                FilledIconButton(
                                                    onClick = { viewModel.updateFontSize(-4f) },
                                                    modifier = Modifier.size(28.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1D2233))
                                                ) {
                                                    Text("A-", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${fontSizeSp.toInt()}sp",
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                FilledIconButton(
                                                    onClick = { viewModel.updateFontSize(4f) },
                                                    modifier = Modifier.size(28.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1D2233))
                                                ) {
                                                    Text("A+", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        // Column 3: Autoplay Speed control
                                        Column(
                                            modifier = Modifier.weight(1.2f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text("Autoplay: $autoplaySeconds s/verso", fontSize = 11.sp, color = StageTextMuted, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                IconButton(
                                                    onClick = { viewModel.updateAutoplaySpeed(autoplaySeconds - 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.updateAutoplaySpeed(autoplaySeconds + 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { viewModel.toggleAutoplay() },
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(
                                                            if (isAutoplayActive) StageNextYellow else Color(0xFF1E2130),
                                                            CircleShape
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = if (isAutoplayActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "Autoplay",
                                                        tint = if (isAutoplayActive) Color.Black else Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick back/forward physical tape selectors at the bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                                    .padding(if (isFullScreenPrompter) 8.dp else 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFullScreenPrompter) {
                                        IconButton(
                                            onClick = { viewModel.selectPreviousLine() },
                                            enabled = currentLineIndex > 0,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NavigateBefore,
                                                contentDescription = "Anterior",
                                                tint = if (currentLineIndex > 0) Color.White else Color.DarkGray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Text(
                                            text = "Verso ${currentLineIndex + 1} de ${lines.size}",
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        IconButton(
                                            onClick = { viewModel.selectNextLine() },
                                            enabled = currentLineIndex < lines.lastIndex,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NavigateNext,
                                                contentDescription = "Próximo",
                                                tint = if (currentLineIndex < lines.lastIndex) Color.White else Color.DarkGray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { viewModel.selectPreviousLine() },
                                            enabled = currentLineIndex > 0,
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E1E1E))
                                        ) {
                                            Icon(Icons.Default.NavigateBefore, contentDescription = "Anterior", tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Verso Anterior", fontSize = 12.sp, color = Color.White)
                                        }

                                        Text(
                                            text = "Linha ${currentLineIndex + 1} de ${lines.size}",
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        FilledTonalButton(
                                            onClick = { viewModel.selectNextLine() },
                                            enabled = currentLineIndex < lines.lastIndex,
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E1E1E))
                                        ) {
                                            Text("Próximo Verso", fontSize = 12.sp, color = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.NavigateNext, contentDescription = "Próximo", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty State (No song selected)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(36.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Nenhuma música carregada",
                                tint = StageTextMuted,
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(bottom = 16.dp)
                            )
                            Text(
                                text = "Sem Música Escolhida",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Crie uma nova letra clicando em '+' à esquerda ou escolha uma das canções gravadas no repertório para entrar no modo teleprompter de palco.",
                                color = StageTextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 480.dp)
                            )
                        }
                    }
                } // closes Box of the Right Column
            } // closes if (isTablet || showPrompterOnMobile)
        } // closes Row

        // FAB for mobile when in library view
        if (!isTablet && !showPrompterOnMobile && selectedSong != null) {
            ExtendedFloatingActionButton(
                onClick = { showPrompterOnMobile = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Ver Letra")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Letra: ${selectedSong?.title ?: ""}", fontWeight = FontWeight.Bold)
            }
        }
    } // closes BoxWithConstraints

            // MODAL DIALOGS -> ADD / CREATE
            if (showAddSongDialog) {
                AddEditSongDialog(
                    isEdit = false,
                    onDismiss = { viewModel.showAddSongDialog.value = false },
                    onSave = { viewModel.saveNewSong() },
                    viewModel = viewModel
                )
            }

            // MODAL DIALOGS -> EDIT
            if (showEditSongDialog) {
                AddEditSongDialog(
                    isEdit = true,
                    onDismiss = { viewModel.showEditSongDialog.value = false },
                    onSave = { viewModel.saveEditedSong() },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun SongGridItem(
    song: Song,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("song_item_${song.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E2436) else Color(0xFF111420)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else StageGridLine
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1C2030),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isSelected) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = StageTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (song.isCustom) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2C193D), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("USER", color = Color(0xFFD3A3FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SyncStatusBadge(isListening: Boolean, syncStatus: SyncStatus) {
    val containerColor = when {
        !isListening -> Color(0xFF21242E)
        syncStatus == SyncStatus.MATCH_FOUND -> Color(0xFF0C4D1D)
        else -> Color(0xFF0A2B42)
    }

    val contentColor = when {
        !isListening -> StageTextMuted
        syncStatus == SyncStatus.MATCH_FOUND -> StageActiveGreen
        else -> Color(0xFF4AC3FF)
    }

    val labelText = when {
        !isListening -> "MICROFONE DESATIVADO"
        syncStatus == SyncStatus.MATCH_FOUND -> "VOCAL SINCRONIZADO"
        else -> "ESCUTANDO VAZÃO..."
    }

    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(100.dp))
            .border(1.dp, contentColor.copy(alpha = 0.3F), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Little pulse green light
            if (isListening) {
                val infiniteTransition = rememberInfiniteTransition()
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(contentColor.copy(alpha = pulseAlpha), CircleShape)
                )
            }
            Text(
                text = labelText,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun LiveSineWaveCanvas(rmsDb: Float, isFullScreen: Boolean = false) {
    // Generate horizontal phase shifts on a loop
    val infiniteTransition = rememberInfiniteTransition()
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isFullScreen) 24.dp else 48.dp)
            .background(Color.Black)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val path = Path()

        // Amplify wav amplitude based on continuous microphone output
        val baseAmplitude = if (rmsDb > 0.05f) rmsDb * 15f + 2f else 2f

        path.moveTo(0f, midY)

        for (x in 0..width.toInt() step 4) {
            val relativeX = x.toFloat() / width
            // Sine math: combine local phase shift and spatial factors
            val sineValue = Math.sin((relativeX * Math.PI * 4.5).toDouble() + phaseShift).toFloat()
            // Squeeze envelope near screen edges so waveform fades cleanly to 0
            val edgeEnvelope = Math.sin((relativeX * Math.PI).toDouble()).toFloat()

            val y = midY + sineValue * baseAmplitude * edgeEnvelope
            path.lineTo(x.toFloat(), y)
        }

        path.lineTo(width, midY)

        val brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF888888),
                Color(0xFFFFFFFF),
                Color(0xFF888888),
            )
        )

        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = if (isFullScreen) 1.5.dp.toPx() else 2.5.dp.toPx())
        )
    }
}

@Composable
fun PrompterLyricRow(
    text: String,
    isCurrent: Boolean,
    isNext: Boolean,
    isPast: Boolean,
    fontSizeSp: Float,
    isFullScreen: Boolean = false,
    onClick: () -> Unit
) {
    // Elegant springs for balanced and organic, yet prompt property transitions
    val animationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
    val colorAnimationSpec = remember {
        spring<Color>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }
    val dpAnimationSpec = remember {
        spring<Dp>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    }

    val scaleMultiplier = if (isCurrent) (if (isFullScreen) 1.08f else 1.15f) else 1.0f
    val animatedScale by animateFloatAsState(
        targetValue = scaleMultiplier,
        animationSpec = animationSpec
    )

    val contentAlpha = when {
        isCurrent -> 1.0f
        isNext -> 0.8f
        isPast -> 0.35f
        else -> 0.40f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = contentAlpha,
        animationSpec = animationSpec
    )

    val containerColor = if (isCurrent) Color(0xFF141414) else Color.Transparent
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = colorAnimationSpec
    )

    val labelColor = when {
        isCurrent -> Color.White
        isNext -> Color(0xFFCCCCCC)
        isPast -> Color(0xFF555555)
        else -> Color(0xFF888888)
    }
    val animatedLabelColor by animateColorAsState(
        targetValue = labelColor,
        animationSpec = colorAnimationSpec
    )

    val targetHorizontalPadding = if (isCurrent) 0.dp else 12.dp
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = targetHorizontalPadding,
        animationSpec = dpAnimationSpec
    )

    val targetVerticalPadding = if (isCurrent) (if (isFullScreen) 16.dp else 24.dp) else (if (isFullScreen) 8.dp else 12.dp)
    val animatedVerticalPadding by animateDpAsState(
        targetValue = targetVerticalPadding,
        animationSpec = dpAnimationSpec
    )

    val targetBorderColor = if (isCurrent) Color.White.copy(alpha = 0.8f) else Color.Transparent
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = colorAnimationSpec
    )

    val targetBorderWidth = if (isCurrent) 1.5.dp else 0.dp
    val animatedBorderWidth by animateDpAsState(
        targetValue = targetBorderWidth,
        animationSpec = dpAnimationSpec
    )

    val borderStroke = if (animatedBorderWidth > 0.dp) {
        BorderStroke(animatedBorderWidth, animatedBorderColor)
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = animatedHorizontalPadding)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor),
        border = borderStroke,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = animatedVerticalPadding,
                    horizontal = 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Only show labels in standard non-fullscreen mode to keep fullscreen stage purely focused on lyrics
            if (!isFullScreen) {
                if (isCurrent) {
                    Text(
                        text = "► AGORA TOCANDO ◄",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                if (isNext) {
                    Text(
                        text = "A SEGUIR",
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            val currentFontSize = if (isCurrent) fontSizeSp else (fontSizeSp * 0.62f)
            val animatedFontSize by animateFloatAsState(
                targetValue = currentFontSize,
                animationSpec = animationSpec
            )
            Text(
                text = text,
                fontSize = animatedFontSize.sp,
                lineHeight = (animatedFontSize * 1.45f).sp,
                color = animatedLabelColor.copy(alpha = animatedAlpha),
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AddEditSongDialog(
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    viewModel: LyricsViewModel
) {
    val formTitle by viewModel.formTitle.collectAsState()
    val formArtist by viewModel.formArtist.collectAsState()
    val formLyrics by viewModel.formLyrics.collectAsState()
    val isGeneratingWithAi by viewModel.isGeneratingWithAi.collectAsState()
    val aiGenerationError by viewModel.aiGenerationError.collectAsState()

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .wrapContentHeight()
                .border(1.dp, StageGridLine, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Editar Letra de Música" else "Nova Letra de Música",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = { onDismiss() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable fields form container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = formTitle,
                        onValueChange = { viewModel.formTitle.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("song_title_input"),
                        label = { Text("Título da Música (Obrigatório)", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = StageGridLine,
                            focusedContainerColor = Color(0xFF0F1118),
                            unfocusedContainerColor = Color(0xFF0F1118)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formArtist,
                        onValueChange = { viewModel.formArtist.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("song_artist_input"),
                        label = { Text("Artista/Banda", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = StageGridLine,
                            focusedContainerColor = Color(0xFF0F1118),
                            unfocusedContainerColor = Color(0xFF0F1118)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // AI Trigger Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.generateLyricsWithGemini() },
                            enabled = !isGeneratingWithAi && formTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F278C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_lyrics_ai_button")
                        ) {
                            if (isGeneratingWithAi) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Obtendo Letras por Gemini... ", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inserir Letras Automaticamente com I.A.", fontSize = 12.sp)
                            }
                        }
                    }

                    if (aiGenerationError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = aiGenerationError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = formLyrics,
                        onValueChange = { viewModel.formLyrics.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("song_lyrics_input"),
                        label = { Text("Letra da Música (Pule linha para definir novo verso)", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = StageGridLine,
                            focusedContainerColor = Color(0xFF0F1118),
                            unfocusedContainerColor = Color(0xFF0F1118)
                        ),
                        maxLines = 100
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = { onSave() },
                        enabled = formTitle.isNotBlank() && formLyrics.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("save_song_confirm_button")
                    ) {
                        Text(if (isEdit) "Salvar Alterações" else "Cadastrar Música", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
