package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import java.io.File
import javax.inject.Inject

class ListenActivityBlockRepository
@Inject
constructor(
    @ApplicationContext private val context: Context
) {

    fun getMinutesPerArtist(): Map<String, Double> {
        val dir = File(context.filesDir, "artist_listening_data")
        if (!dir.exists() || !dir.isDirectory) return emptyMap()

        Log.d("ListenActivityRepo", "Reading listen activity data from: ${dir.absolutePath}")

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.associate { file ->
                val artist = file.name.removeSuffix(".txt")
                val millis = file.readText().toLongOrNull() ?: 0L
                artist to (millis / 60000.0)
            } ?: emptyMap()
    }
}
