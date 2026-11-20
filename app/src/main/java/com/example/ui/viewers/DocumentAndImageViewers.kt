package com.example.ui.viewers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.FileEntry
import com.example.model.Formatters
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ImageZoomViewerOverlay(
    entry: FileEntry,
    uriString: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = Uri.parse(uriString),
                contentDescription = entry.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }

        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC09090B))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .align(Alignment.TopCenter)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("image_close_button")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close image", tint = Color.White)
            }
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showInfo = !showInfo }) {
                Icon(Icons.Outlined.Info, contentDescription = "Image details", tint = Color.White)
            }
        }

        // Bottom Zoom Slider & Metadata
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC09090B))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.BottomCenter)
        ) {
            if (showInfo) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141418),
                    border = BorderStroke(1.dp, Color(0xFF27272A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${entry.mimeType} · ${Formatters.formatBytes(entry.sizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Modified: ${Formatters.formatDate(entry.lastModifiedMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ZOOM ${String.format(Locale.US, "%.1fx", scale)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it
                        if (it <= 1.01f) offset = Offset.Zero
                    },
                    valueRange = 1f..5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0xFF3F3F46)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("image_zoom_slider")
                )
            }
        }
    }
}

@Composable
fun PdfPreviewViewerOverlay(
    entry: FileEntry,
    filePath: String,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var descriptor by remember(filePath) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(filePath) { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(filePath) {
        val result = runCatching {
            val pfd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            descriptor = pfd
            renderer = r
            pageCount = r.pageCount
        }
        if (result.isFailure) {
            errorText = result.exceptionOrNull()?.message ?: "Unable to render PDF"
        }
        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            pageBitmap?.recycle()
        }
    }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        if (pageIndex !in 0 until r.pageCount) return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) {
            runCatching {
                r.openPage(pageIndex).use { page ->
                    val width = (page.width * 2).coerceIn(600, 2048)
                    val height = (page.height * 2).coerceIn(800, 2800)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()
        }
        pageBitmap = rendered
        scale = 1f
        offset = Offset.Zero
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("pdf_close_button")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close PDF")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pageCount > 0) "Page ${pageIndex + 1} of $pageCount" else "PDF Document",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // PDF Canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 4f)
                            scale = next
                            offset = if (next > 1f) offset + pan else Offset.Zero
                        }
                    }
            ) {
                when {
                    errorText != null -> Text(errorText.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pageBitmap != null -> Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                    else -> CircularProgressIndicator()
                }
            }

            // Bottom Page Slider Controls
            if (pageCount > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { if (pageIndex > 0) pageIndex-- },
                        enabled = pageIndex > 0
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous page")
                    }
                    Slider(
                        value = pageIndex.toFloat(),
                        onValueChange = { pageIndex = it.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0)) },
                        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_page_slider")
                    )
                    IconButton(
                        onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                        enabled = pageIndex < pageCount - 1
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}

@Composable
fun TextDocumentViewerOverlay(
    entry: FileEntry,
    content: String,
    truncated: Boolean,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }

    val lines = remember(content, searchTerm) {
        val raw = content.lines()
        if (searchTerm.isBlank()) {
            raw.mapIndexed { idx, line -> idx + 1 to line }
        } else {
            raw.mapIndexedNotNull { idx, line ->
                if (line.contains(searchTerm, ignoreCase = true)) (idx + 1 to line) else null
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("text_close_button")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close text viewer")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${Formatters.formatBytes(entry.sizeBytes)} · ${lines.size} lines${if (truncated) " (preview capped at 256 KB)" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showLineNumbers = !showLineNumbers }) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = "Toggle line numbers")
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search text")
                }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { searchTerm = it },
                    placeholder = { Text("Filter matching lines…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Scrollable monospace text pane
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                lines.forEach { (lineNumber, textLine) ->
                    Row {
                        if (showLineNumbers) {
                            Text(
                                text = String.format(Locale.US, "%4d  ", lineNumber),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = textLine,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "FONT ${fontSizeSp.roundToInt()}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 10f..24f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_font_size_slider")
                )
            }
        }
    }
}
