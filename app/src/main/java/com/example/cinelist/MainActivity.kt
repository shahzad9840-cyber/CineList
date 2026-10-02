package com.example.cinelist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

data class Movie(val id: Int, val title: String, val sub: String, val poster: String?)

val TABS = listOf(
    "Trending" to "/trending/movie/week", "Popular" to "/movie/popular",
    "Top rated" to "/movie/top_rated", "Upcoming" to "/movie/upcoming"
)

suspend fun api(key: String, path: String, extra: String = ""): JSONObject =
    withContext(Dispatchers.IO) {
        JSONObject(URL("https://api.themoviedb.org/3$path?api_key=$key&language=en-US$extra").readText())
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", Context.MODE_PRIVATE) }
    var key by remember { mutableStateOf(prefs.getString("k", "") ?: "") }
    var showKey by remember { mutableStateOf(key.isEmpty()) }
    var tab by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf(listOf<Movie>()) }
    var err by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Movie?>(null) }

    LaunchedEffect(key, tab, q) {
        if (key.isEmpty()) return@LaunchedEffect
        if (q.isNotBlank()) delay(450)
        err = ""
        try {
            val d = if (q.isBlank()) api(key, TABS[tab].second)
            else api(key, "/search/movie", "&query=" + URLEncoder.encode(q, "UTF-8"))
            val r = d.getJSONArray("results")
            movies = (0 until r.length()).map {
                val o = r.getJSONObject(it)
                Movie(
                    o.getInt("id"), o.getString("title"),
                    o.optString("release_date").take(4) + " ★ " + "%.1f".format(o.optDouble("vote_average")),
                    o.optString("poster_path").takeIf { p -> p.isNotEmpty() && p != "null" }
                )
            }
        } catch (e: Exception) { err = "Load nahi hua. Key aur internet check karo." }
    }

    Column(Modifier.statusBarsPadding().padding(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Movie search karo") })
            TextButton({ showKey = true }) { Text("Key") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEachIndexed { i, t ->
                FilterChip(selected = q.isBlank() && tab == i, onClick = { q = ""; tab = i }, label = { Text(t.first) })
            }
        }
        if (err.isNotEmpty()) Text(err)
        LazyVerticalGrid(GridCells.Adaptive(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(movies, key = { it.id }) { m ->
                Column(Modifier.clickable { sel = m }) {
                    AsyncImage(
                        model = m.poster?.let { "https://image.tmdb.org/t/p/w185$it" },
                        contentDescription = m.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    )
                    Text(m.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(m.sub, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showKey) {
        var tmp by remember { mutableStateOf(key) }
        AlertDialog(
            onDismissRequest = { if (key.isNotEmpty()) showKey = false },
            title = { Text("TMDB API key") },
            text = { OutlinedTextField(tmp, { tmp = it }, singleLine = true, label = { Text("themoviedb.org se free key") }) },
            confirmButton = {
                TextButton({
                    key = tmp.trim(); prefs.edit().putString("k", key).apply(); showKey = key.isEmpty()
                }) { Text("Save") }
            }
        )
    }

    sel?.let { m ->
        var info by remember(m.id) { mutableStateOf("") }
        var yt by remember(m.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(m.id) {
            try {
                val d = api(key, "/movie/${m.id}", "&append_to_response=videos")
                info = d.optString("overview").ifEmpty { "Description available nahi hai." }
                val v = d.getJSONObject("videos").getJSONArray("results")
                for (i in 0 until v.length()) {
                    val o = v.getJSONObject(i)
                    if (o.optString("site") == "YouTube") { yt = o.getString("key"); if (o.optString("type") == "Trailer") break }
                }
            } catch (e: Exception) { info = "Load nahi hua." }
        }
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(m.title) },
            text = { Text(info.ifEmpty { "Load ho raha hai…" }) },
            confirmButton = {
                TextButton(enabled = yt != null, onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$yt")))
                }) { Text("Trailer dekho") }
            },
            dismissButton = { TextButton({ sel = null }) { Text("Band") } }
        )
    }
}
