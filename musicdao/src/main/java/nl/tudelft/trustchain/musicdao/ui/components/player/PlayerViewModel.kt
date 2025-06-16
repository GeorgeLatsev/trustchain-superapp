@file:Suppress("DEPRECATION")

package nl.tudelft.trustchain.musicdao.ui.components.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import nl.tudelft.trustchain.musicdao.core.repositories.model.Song
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class PlayerViewModel(context: Context) : ViewModel() {
    private val _playingTrack: MutableStateFlow<Song?> = MutableStateFlow(null)
    val playingTrack: StateFlow<Song?> = _playingTrack

    private val _coverFile: MutableStateFlow<File?> = MutableStateFlow(null)
    val coverFile: StateFlow<File?> = _coverFile

    val exoPlayer by lazy {
        ExoPlayer.Builder(context).build()
    }

    private var appDir: File? = context.filesDir
    private var currentArtist: String? = null
    private var currentArtistPublisher: String? = null
    private var startTime: Long = 0L
    private var endTime: Long = 0L

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.d("ExoPlayerListener", "Playback state changed: $state")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("ExoPlayerListener", "Is playing: $isPlaying")
                if (isPlaying) {
                    startTime = System.currentTimeMillis()
                } else {
                    endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    if (currentArtist!!.contains('|')){
                        Log.d("ExoPlayerListener", "Track played for: $duration ms by $currentArtist")
                        updateListenActivity(currentArtist, duration)
                    } else {
                        Log.d("ExoPlayerListener", "Track played for: $duration ms by $currentArtistPublisher")
                        updateListenActivity(currentArtistPublisher, duration)
                    }
                }
            }
        })
    }

    private fun updateListenActivity(artist: String?, duration: Long) {
        if (artist == null) return

        val dir = File(appDir, "artist_listening_data")
        if (!dir.exists()) {
            dir.mkdirs() // Create the directory if it doesn't exist
        }

        val artistFile = File(dir, "$artist.txt")
        val previousDuration = if (artistFile.exists()) {
            artistFile.readText().toLongOrNull() ?: 0L
        } else {
            0L
        }
        val totalDuration = previousDuration + duration
        artistFile.writeText(totalDuration.toString())
        Log.d("StatsUpdate", "Artist file path: ${artistFile.absolutePath}")
        Log.d("StatsUpdate", "Updated listening time for $artist: $totalDuration ms")
    }

    private fun buildMediaSource(
        uri: Uri,
        context: Context
    ): MediaSource {
        @Suppress("DEPRECATION")
        val dataSourceFactory: DataSource.Factory =
            DefaultDataSourceFactory(context, "musicdao-audioplayer")
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    fun playDownloadedTrack(
        track: Song,
        cover: File? = null,
        album: Album

    ) {
        _playingTrack.value = track
        _coverFile.value = cover
        val mediaItem = MediaItem.fromUri(Uri.fromFile(track.file))
        Log.d("MusicDAOTorrent", "Trying to play ${track.file}")
        exoPlayer.playWhenReady = true
        exoPlayer.seekTo(0, 0)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        currentArtist = track.artist
        currentArtistPublisher = album.publisher
    }

    fun playDownloadingTrack(
        track: Song,
        context: Context,
        cover: File? = null,
        album: Album
    ) {
        _playingTrack.value = track
        _coverFile.value = cover
        val mediaSource =
            buildMediaSource(Uri.fromFile(track.file), context)
        Log.d("MusicDAOTorrent", "Trying to play ${track.file}")
        exoPlayer.playWhenReady = true
        exoPlayer.seekTo(0, 0)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
        currentArtist = track.artist
        currentArtistPublisher = album.publisher
    }

    fun release() {
        exoPlayer.release()
        exoPlayer.stop()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        context
                    ) as T
                }
            }
    }
}
