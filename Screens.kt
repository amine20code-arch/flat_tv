@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.streamtv.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.material3.Text as M3Text

enum class Section(val icon: String, val label: String) {
    HOME("🏠", "Home"), SEARCH("🔍", "Search"), LIVE("📺", "Live TV"), MOVIES("🎬", "Movies"), SERIES("🍿", "Series"),
    MATCHES("⚽", "Matches"), RADIO("📻", "Radio"), SOURCES("➕", "Sources"), SETTINGS("⚙", "Settings")
}

typealias OpenFn = (List<ChannelEntity>, Int, Long) -> Unit

private val SPORT_RE = Regex("(?i)sport|bein|ssc|match|football|soccer|ligue|liga|premier|champions|كأس|رياض|مباريات|كرة")

fun toast(ctx: Context, msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

fun launchTrailer(ctx: Context, scope: CoroutineScope, vm: MainViewModel, c: ChannelEntity) {
    scope.launch {
        val t = vm.trailer(c)
        if (t == null) toast(ctx, "No trailer available")
        else {
            val u = if (t.startsWith("http")) t else "https://www.youtube.com/watch?v=$t"
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        }
    }
}

// =====================================================================================================
// Home shell: collapsible side menu + current section
// =====================================================================================================

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by vm.theme.collectAsStateWithLifecycle()
    var episodes by remember { mutableStateOf<List<ChannelEntity>?>(null) }

    val open: OpenFn = { list, idx, resume ->
        val target = list[idx]
        if (target.kind == "series") {
            scope.launch {
                val e = vm.episodes(target)
                if (e.isEmpty()) toast(ctx, "No episodes found") else episodes = e
            }
        } else vm.playFull(list, idx, resume)
    }

    NavigationDrawer(drawerContent = {
        Column(Modifier.fillMaxHeight().background(Color(0x99000000)).padding(12.dp), verticalArrangement = Arrangement.Center) {
            Section.values().forEach { sec ->
                NavigationDrawerItem(selected = vm.section == sec, onClick = { vm.section = sec }, leadingContent = { Text(sec.icon) }) { Text(sec.label) }
            }
        }
    }) {
        Box(Modifier.fillMaxSize().background(theme.bg).padding(start = 80.dp, top = 16.dp, end = 12.dp)) {
            when (vm.section) {
                Section.HOME -> HomeSection(vm, open)
                Section.SEARCH -> SearchScreen(vm, open)
                Section.LIVE -> BrowseScreen(vm, "live", null, "live", open)
                Section.MOVIES -> BrowseScreen(vm, "movie", null, "movie", open)
                Section.SERIES -> BrowseScreen(vm, "series", null, "series", open)
                Section.MATCHES -> BrowseScreen(vm, "live", SPORT_RE, "matches", open)
                Section.RADIO -> BrowseScreen(vm, "radio", null, "radio", open)
                Section.SOURCES -> SourcesScreen(vm)
                Section.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
    episodes?.let { eps ->
        Dialog(onDismissRequest = { episodes = null }) {
            Box(Modifier.width(520.dp).heightIn(max = 460.dp).background(Color(0xEE111111)).padding(16.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(eps) { i, e ->
                        Button(onClick = { episodes = null; vm.playFull(eps, i, 0L) }, modifier = Modifier.fillMaxWidth()) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================================
// Home section: hero + continue watching + my list (kept light on purpose)
// =====================================================================================================

@Composable
fun HomeSection(vm: MainViewModel, open: OpenFn) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hist by vm.history.collectAsStateWithLifecycle(emptyList())
    val favs by vm.favorites.collectAsStateWithLifecycle(emptyList())
    val count by vm.itemCount.collectAsStateWithLifecycle()
    val hero by produceState(emptyList<ChannelEntity>(), count) {
        val m = vm.featured("movie")
        value = if (m.isNotEmpty()) m else vm.featured("series")
    }
    if (count == 0) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Welcome! Open the menu and choose Sources to add your first source.") }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (hero.isNotEmpty()) item { Hero(hero, vm, { open(listOf(it), 0, 0L) }, { launchTrailer(ctx, scope, vm, it) }) }
        if (hist.isNotEmpty()) item {
            val list = hist.map { it.toItem() }
            ItemRow("Continue Watching", list, { i -> open(list, i, hist[i].position) }, vm)
        }
        if (favs.isNotEmpty()) item { ItemRow("My List", favs, { i -> open(favs, i, 0L) }, vm) }
        item { Text("Use the side menu: Live TV, Movies, Series, Matches, Radio.", color = Color.Gray, fontSize = 12.sp) }
    }
}

@Composable
fun Hero(items: List<ChannelEntity>, vm: MainViewModel, onWatch: (ChannelEntity) -> Unit, onTrailer: (ChannelEntity) -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    val lite = vm.settings.lite.on
    val bg = MaterialTheme.colorScheme.background
    LaunchedEffect(items, lite) { while (!lite && items.size > 1) { delay(8000); i = (i + 1) % items.size } }
    val cur = items.getOrNull(i % items.size) ?: return
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp))) {
        AsyncImage(model = cur.logo.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Transparent))))
        Column(Modifier.padding(24.dp).width(520.dp).align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cur.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (cur.rating.isNotBlank() && cur.rating != "0") Text("★ ${cur.rating}", color = Color(0xFFF5C518))
                Text(cur.groupTitle, color = Color.LightGray)
            }
            if (cur.plot.isNotBlank()) Text(cur.plot.take(160), maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onWatch(cur) }) { Text("Watch Now") }
                Button(onClick = { onTrailer(cur) }) { Text("Trailer") }
                Button(onClick = { vm.toggleFav(cur) }) { Text("My List") }
            }
        }
    }
}

@Composable
fun ItemRow(title: String?, items: List<ChannelEntity>, onOpen: (Int) -> Unit, vm: MainViewModel) {
    val ctx = LocalContext.current
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 48.dp, top = 8.dp, bottom = 8.dp)) {
            itemsIndexed(items) { i, c -> MediaCard(c, { onOpen(i) }, { vm.toggleFav(c); toast(ctx, "My List updated") }) }
        }
    }
}

@Composable
fun MediaCard(c: ChannelEntity, onClick: () -> Unit, onFav: () -> Unit) {
    val poster = c.kind == "movie" || c.kind == "series"
    Card(
        onClick = onClick, onLongClick = onFav,
        modifier = Modifier.width(if (poster) 120.dp else 140.dp),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(8.dp)))
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = c.logo.ifBlank { null }, contentDescription = null,
                contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.height(if (poster) 150.dp else 60.dp).fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// =====================================================================================================
// Master / detail browser:  categories  ->  items  ->  preview window (live) or details (movies/series)
// =====================================================================================================

@Composable
fun CatRow(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (selected) "▸ " else "") + title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                modifier = Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else Color.White
            )
            Text("$count", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ChannelRow(
    c: ChannelEntity, size: Int, highlighted: Boolean, lite: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier
) {
    val poster = c.kind == "movie" || c.kind == "series"
    Card(
        onClick = onClick, onLongClick = onLong, modifier = modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = if (lite) 1f else 1.03f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp)))
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = c.logo.ifBlank { null }, contentDescription = null,
                contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.width(if (poster) (size * 0.7f).dp else size.dp).height(if (poster) (size * 1.2f).dp else size.dp)
            )
            Text(
                c.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.weight(1f),
                color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

@Composable
fun BrowseScreen(vm: MainViewModel, kind: String, only: Regex?, stateKey: String, open: OpenFn) {
    val ctx = LocalContext.current
    val s = vm.session
    val st = vm.settings
    val iconSize = st.iconSize.int
    val lite = st.lite.on
    val hideAdult = st.hideAdult.on
    val isLiveKind = kind == "live" || kind == "radio"

    val groups by remember(kind, hideAdult) { vm.groups(kind) }.collectAsStateWithLifecycle(emptyList())
    val hist by remember(kind) { vm.historyOf(kind) }.collectAsStateWithLifecycle(emptyList())
    val favs by remember(kind) { vm.favoritesOf(kind) }.collectAsStateWithLifecycle(emptyList())
    val shown = remember(groups, only) { if (only != null) groups.filter { only.containsMatchIn(it.groupTitle) } else groups }
    val cat = vm.cats[stateKey]
    val groupItems by remember(kind, cat) {
        if (cat == null || cat == FAV_KEY || cat == RECENT_KEY) flowOf(emptyList<ChannelEntity>()) else vm.channels(kind, cat)
    }.collectAsStateWithLifecycle(emptyList())
    val list = remember(cat, favs, hist, groupItems) {
        when (cat) {
            null -> emptyList()
            FAV_KEY -> favs
            RECENT_KEY -> hist.map { it.toItem() }
            else -> groupItems
        }
    }
    val posMap = remember(hist) { hist.associate { it.itemId to it.position } }

    val catList = rememberLazyListState(vm.scrollPos["c$stateKey"] ?: 0)
    val itemList = rememberLazyListState(vm.scrollPos["i$stateKey"] ?: 0)
    val playFocus = remember { FocusRequester() }
    val firstRun = remember { booleanArrayOf(true) }

    LaunchedEffect(shown) { if (vm.cats[stateKey] == null && shown.isNotEmpty()) vm.cats[stateKey] = shown.first().groupTitle }
    LaunchedEffect(cat) {
        if (firstRun[0]) { firstRun[0] = false } else { runCatching { itemList.scrollToItem(0) } }
    }
    LaunchedEffect(Unit) {
        if (vm.restoreFocus) {
            vm.restoreFocus = false
            delay(250)
            runCatching { playFocus.requestFocus() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.scrollPos["c$stateKey"] = catList.firstVisibleItemIndex
            vm.scrollPos["i$stateKey"] = itemList.firstVisibleItemIndex
            if (s.previewMode && !s.fullscreen) s.stop() // leaving the section stops the preview
        }
    }

    if (shown.isEmpty() && favs.isEmpty() && hist.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                when (kind) {
                    "movie" -> "No movies. Add an Xtream source that includes VOD."
                    "series" -> "No series. Add an Xtream source that includes series."
                    "radio" -> "No radio stations (categories named Radio are detected)."
                    else -> if (only != null) "No sports categories found in your sources." else "No channels yet. Open the menu and choose Sources."
                }
            )
        }
        return
    }

    val pick: (Int) -> Unit = { i ->
        val c = list[i]
        if (isLiveKind) {
            if (s.item?.id == c.id && s.engineKind.isNotEmpty() && s.previewMode) s.fullscreen = true
            else if (st.clickMode.v == "full") s.play(list, i, full = true, preview = true)
            else s.play(list, i, preview = true)
        } else vm.selected[stateKey] = c
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- column 1: categories
        LazyColumn(
            Modifier.width(190.dp).fillMaxHeight(), state = catList,
            verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item { CatRow("★ My List", favs.size, cat == FAV_KEY) { vm.cats[stateKey] = FAV_KEY } }
            item { CatRow("⏱ Recent", hist.size, cat == RECENT_KEY) { vm.cats[stateKey] = RECENT_KEY } }
            items(shown) { g -> CatRow(g.groupTitle, g.c, cat == g.groupTitle) { vm.cats[stateKey] = g.groupTitle } }
        }
        // ---- column 2: channels / movies / series
        LazyColumn(
            Modifier.width(300.dp).fillMaxHeight(), state = itemList,
            verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            if (list.isEmpty()) item { Text("Nothing here yet", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
            itemsIndexed(list) { i, c ->
                val playing = isLiveKind && s.engineKind.isNotEmpty() && s.item?.id == c.id
                val chosen = vm.selected[stateKey]?.id == c.id
                ChannelRow(
                    c, iconSize, playing || chosen, lite,
                    onClick = { pick(i) },
                    onLong = { vm.toggleFav(c); toast(ctx, "My List updated") },
                    modifier = if (playing) Modifier.focusRequester(playFocus) else Modifier
                )
            }
        }
        // ---- column 3: preview window or details
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (isLiveKind) LivePane(vm)
            else {
                val sel = vm.selected[stateKey]
                if (sel == null) Text("Select an item to see its details", color = Color.Gray)
                else if (kind == "movie") MovieDetail(vm, sel, posMap[sel.id] ?: 0L)
                else SeriesDetail(vm, sel)
            }
        }
    }
}

@Composable
fun LivePane(vm: MainViewModel) {
    val s = vm.session
    val ctx = LocalContext.current
    val cur = s.item
    val epg by produceState(emptyList<EpgEntity>(), cur?.id) {
        val t = cur?.tvgId
        value = if (!t.isNullOrBlank()) runCatching { vm.repo.nowNext(t) }.getOrDefault(emptyList()) else emptyList()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cur != null && s.engineKind.isNotEmpty() && s.previewMode) {
            MiniPlayer(vm, Modifier.fillMaxWidth())
            Text(cur.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            epg.getOrNull(0)?.let { Text("Now: ${it.title}", fontSize = 13.sp) }
            epg.getOrNull(1)?.let { Text("Next: ${it.title}", fontSize = 12.sp, color = Color.Gray) }
            s.error?.let { Text(it, color = Color(0xFFFF8888), fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { s.fullscreen = true }) { Text("Fullscreen") }
                Button(onClick = { vm.toggleFav(cur); toast(ctx, "My List updated") }) { Text("My List") }
                Button(onClick = { s.stop() }) { Text("Stop") }
            }
            Text("OK on the window = fullscreen  -  hold OK on a channel = My List", fontSize = 11.sp, color = Color.Gray)
        } else {
            Text("Select a channel to watch it in this preview window", color = Color.Gray)
        }
    }
}

@Composable
fun MovieDetail(vm: MainViewModel, c: ChannelEntity, resume: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = c.logo.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(130.dp).height(190.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (c.rating.isNotBlank() && c.rating != "0") Text("★ ${c.rating}", color = Color(0xFFF5C518))
                Text(c.groupTitle, color = Color.LightGray, fontSize = 13.sp)
            }
            if (c.plot.isNotBlank()) Text(c.plot, maxLines = 6, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            if (resume > 5000) Text("Resume from ${fmtTime(resume)}", color = Color(0xFF9FD3FF), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.playFull(listOf(c), 0, resume) }) { Text(if (resume > 5000) "Resume" else "Play") }
                if (resume > 5000) Button(onClick = { vm.playFull(listOf(c), 0, 0L) }) { Text("From start") }
                Button(onClick = { launchTrailer(ctx, scope, vm, c) }) { Text("Trailer") }
                Button(onClick = { vm.toggleFav(c); toast(ctx, "My List updated") }) { Text("My List") }
            }
        }
    }
}

@Composable
fun SeriesDetail(vm: MainViewModel, c: ChannelEntity) {
    val ctx = LocalContext.current
    var loading by remember(c.id) { mutableStateOf(true) }
    val eps by produceState(emptyList<ChannelEntity>(), c.id) { value = vm.episodes(c); loading = false }
    val hist by remember { vm.historyOf("movie") }.collectAsStateWithLifecycle(emptyList())
    val pos = remember(hist) { hist.associate { it.itemId to it.position } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = c.logo.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(80.dp).height(115.dp).clip(RoundedCornerShape(6.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.rating.isNotBlank() && c.rating != "0") Text("★ ${c.rating}", color = Color(0xFFF5C518), fontSize = 12.sp)
                if (c.plot.isNotBlank()) Text(c.plot, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Button(onClick = { vm.toggleFav(c); toast(ctx, "My List updated") }) { Text("My List") }
            }
        }
        if (loading) Text("Loading episodes...", color = Color.Gray)
        else if (eps.isEmpty()) Text("No episodes found", color = Color.Gray)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(eps) { i, e ->
                val p = pos[e.id] ?: 0L
                Card(
                    onClick = { vm.playFull(eps, i, p) }, modifier = Modifier.fillMaxWidth(),
                    scale = CardDefaults.scale(focusedScale = 1f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White)))
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (p > 5000) Text("▶ ${fmtTime(p)}", fontSize = 11.sp, color = Color(0xFF9FD3FF))
                    }
                }
            }
        }
    }
}

// =====================================================================================================
// Search, sources, settings, profiles
// =====================================================================================================

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, onFocus: () -> Unit = {}, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { M3Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }
    )
}

@Composable
fun SearchScreen(vm: MainViewModel, open: OpenFn) {
    val ctx = LocalContext.current
    val iconSize = vm.settings.iconSize.int
    val lite = vm.settings.lite.on
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    LaunchedEffect(Unit) { RemoteBus.text.collect { q += it } }
    LaunchedEffect(q) { if (q.length >= 2) { delay(300); results = vm.search(q) } else results = emptyList() }
    Column(Modifier.width(600.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(q, { q = it }, "Search channels, movies, series (the phone keyboard works too)")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(results) { i, c ->
                ChannelRow(c, iconSize, false, lite, onClick = { open(results, i, 0L) }, onLong = { vm.toggleFav(c); toast(ctx, "My List updated") })
            }
        }
    }
}

@Composable
fun SourcesScreen(vm: MainViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var mode by remember { mutableIntStateOf(0) } // 0 M3U, 1 Xtream, 2 Stalker
    var focused by remember { mutableIntStateOf(0) }
    val v = remember { mutableStateListOf("", "", "", "", "") } // 0 name, 1 url, 2 user/mac, 3 pass, 4 epg
    LaunchedEffect(Unit) { RemoteBus.text.collect { v[focused] = v[focused] + it } }
    val idxs = when (mode) { 0 -> listOf(0, 1, 4); 1 -> listOf(0, 1, 2, 3); else -> listOf(0, 1, 2) }
    fun label(i: Int) = when (i) {
        0 -> "Name"
        1 -> listOf("Playlist URL (.m3u / .m3u8)", "Server (http://host:port)", "Portal URL (http://host/c/)")[mode]
        2 -> if (mode == 1) "Username" else "MAC address (00:1A:79:xx:xx:xx)"
        3 -> "Password"
        else -> "EPG XMLTV URL (optional)"
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(600.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { Text("Add a source", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("M3U", "Xtream Codes", "Stalker / MAG").forEachIndexed { i, n -> Button(onClick = { mode = i; focused = 0 }) { Text(if (mode == i) "● $n" else n) } }
            }
        }
        idxs.forEach { i -> item(key = "f$i-$mode") { Field(v[i], { v[i] = it }, label(i), { focused = i }) } }
        item {
            Button(enabled = !busy, onClick = {
                when (mode) { 0 -> vm.addM3u(v[0], v[1], v[4]); 1 -> vm.addXtream(v[0], v[1], v[2], v[3]); else -> vm.addStalker(v[0], v[1], v[2]) }
            }) { Text(if (busy) "Working..." else "Import") }
        }
        item { Text(status, fontSize = 13.sp) }
        item { Text("Your sources", style = MaterialTheme.typography.titleMedium) }
        items(playlists) { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${p.name} (${p.type})", modifier = Modifier.width(240.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Button(enabled = !busy, onClick = { vm.refreshEpg(p) }) { Text("Refresh EPG") }
                Button(onClick = { vm.removePlaylist(p) }) { Text("Delete") }
            }
        }
    }
}

@Composable
fun OptButton(label: String, o: Opt) {
    Button(onClick = { o.cycle() }, modifier = Modifier.fillMaxWidth()) { Text("$label:  ${o.label}") }
}

@Composable
fun SettingsHeader(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val st = vm.settings
    val ctx = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    LazyColumn(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
        item { SettingsHeader("Player") }
        item { OptButton("Default player", st.engine) }
        item { OptButton("Buffering", st.buffer) }
        item { OptButton("Hardware decoding (VLC)", st.hw) }
        item { OptButton("Default aspect ratio", st.aspect) }
        item { OptButton("Seek step (movies)", st.seek) }
        item { OptButton("Resume movies where you stopped", st.resume) }
        item { OptButton("Auto-play next episode", st.autoNext) }
        item { OptButton("Subtitle size", st.subSize) }

        item { SettingsHeader("Interface") }
        item { OptButton("Clicking a channel", st.clickMode) }
        item { OptButton("Channel icon size", st.iconSize) }
        item { OptButton("Lite mode (fewer animations, less memory)", st.lite) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.values().forEach { t -> Button(onClick = { vm.setTheme(t) }) { Text(t.label) } }
            }
        }

        item { SettingsHeader("Content") }
        item { OptButton("EPG window (guide data kept)", st.epgHours) }
        item { OptButton("Hide adult categories", st.hideAdult) }
        item { Button(onClick = { vm.clearHistory(); toast(ctx, "History cleared") }, modifier = Modifier.fillMaxWidth()) { Text("Clear watch history") } }
        item { Button(onClick = { vm.clearFavorites(); toast(ctx, "My List cleared") }, modifier = Modifier.fillMaxWidth()) { Text("Clear My List") } }

        item { SettingsHeader("Profile") }
        item {
            Text("Current: ${profile?.name ?: ""}${if (profile?.isKids == true) " (Kids)" else ""}")
            Spacer(Modifier.height(6.dp))
            Button(onClick = { vm.select(null) }) { Text("Switch profile") }
        }

        item { SettingsHeader("Phone remote") }
        item {
            val url = RemoteInfo.url
            if (url.isBlank()) Text("Remote server unavailable (port in use or no network).")
            else {
                Text("Scan with a phone on the same Wi-Fi: D-pad, keyboard and stream URL cast.", color = Color.LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Image(bitmap = remember(url) { qr(url, 320) }, contentDescription = "QR", modifier = Modifier.size(200.dp).background(Color.White).padding(8.dp))
                Text(url, fontSize = 12.sp)
            }
        }

        item { SettingsHeader("About") }
        item { Text("Stream TV 1.1  -  players: ExoPlayer (Media3) and VLC (libVLC)", fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
fun ProfileScreen(vm: MainViewModel) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val ps by vm.profiles.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(theme.bg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Who's watching?", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ps.forEach { p ->
                Card(
                    onClick = { if (p.pin.isBlank()) vm.select(p) else pinFor = p }, onLongClick = { vm.deleteProfile(p) },
                    scale = CardDefaults.scale(focusedScale = 1.12f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White)))
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp).background(theme.primary, RoundedCornerShape(12.dp)), Alignment.Center) {
                            Text(p.name.take(1).uppercase(), fontSize = 40.sp, color = Color.White)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(p.name + if (p.isKids) " (Kids)" else "")
                        if (p.pin.isNotBlank()) Text("🔒", fontSize = 12.sp)
                    }
                }
            }
            Card(onClick = { adding = true }, scale = CardDefaults.scale(focusedScale = 1.12f)) {
                Box(Modifier.size(132.dp, 160.dp), Alignment.Center) { Text("+  Add", fontSize = 22.sp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Long-press OK on a profile to delete it", color = Color.Gray, fontSize = 12.sp)
    }
    pinFor?.let { p ->
        var pin by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { pinFor = null }) {
            Column(Modifier.background(Color(0xEE111111)).padding(24.dp).width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PIN for ${p.name}")
                Field(pin, { pin = it.filter(Char::isDigit).take(8); wrong = false }, "PIN", secret = true)
                if (wrong) Text("Wrong PIN", color = Color(0xFFFF6666))
                Button(onClick = { if (pin == p.pin) { pinFor = null; vm.select(p) } else wrong = true }) { Text("OK") }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var kids by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { adding = false }) {
            Column(Modifier.background(Color(0xEE111111)).padding(24.dp).width(360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New profile")
                Field(name, { name = it }, "Name")
                Field(pin, { pin = it.filter(Char::isDigit).take(8) }, "PIN (optional)", secret = true)
                Button(onClick = { kids = !kids }) { Text(if (kids) "Kids profile: ON (adult categories hidden)" else "Kids profile: OFF") }
                Button(onClick = { vm.addProfile(name, pin, kids); adding = false }) { Text("Create") }
            }
        }
    }
}
