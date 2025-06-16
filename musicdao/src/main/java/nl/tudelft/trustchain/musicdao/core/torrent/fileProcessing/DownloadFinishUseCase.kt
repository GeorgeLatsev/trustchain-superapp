package nl.tudelft.trustchain.musicdao.core.torrent.fileProcessing

import android.content.Context
import android.util.Log
import org.bitcoinj.core.ECKey
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Address
import org.bitcoinj.core.SegwitAddress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.CachePath
import nl.tudelft.trustchain.musicdao.R
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.cache.entities.SongEntity
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import java.nio.file.Paths

class DownloadFinishUseCase(
    private val database: CacheDatabase,
    private val cachePath: CachePath,
    private val context: Context,
    private val walletService: WalletService
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    operator fun invoke(infoHash: String) {
        coroutineScope.launch {
            Log.d("MusicDao", "DownloadFinishUseCase: $infoHash")

            val metadataInfoHash = magnetToInfoHash(context.getString(R.string.bootstrap_cc_music_metadata));
            if (infoHash == metadataInfoHash) {
                val data = Paths.get("${cachePath.getPath()}/torrents/$infoHash");

                val metadata = data.resolve("metadata.psv").toFile()

                val lines = metadata.readLines()
                for (line in lines) {
                    val songMetadata = line.split('|');
                    database.dao.insert(
                        AlbumEntity(
                            id = "cc:pandacd-" + songMetadata[0],
                            magnet = songMetadata[2],
                            title = songMetadata[0].substringAfter(" – ").substringBefore(" ["),
                            artist = songMetadata[1] + "|" + getDeterministicSegwitAddress(songMetadata[1]),
                            publisher = "creative commons",
                            releaseDate = songMetadata[0].substringBeforeLast("] ").substringAfterLast(" [") + "-01-01T00:00:00Z",
                            songs = listOf(),
                            cover = null,
                            root = null,
                            isDownloaded = false,
                            infoHash = magnetToInfoHash(songMetadata[2]),
                            torrentPath = null
                        )
                    )
                }
            } else {
                // TODO: fix, multiple releases can potentially have some info-hash, will break
                val albumEntities = database.dao.getFromInfoHash(infoHash)

                for (albumEntity in albumEntities) {
                    if (albumEntity.isDownloaded) {
                        Log.d("MusicDao", "DownloadFinishUseCase: Skipping $infoHash of ${albumEntity.id}, already downloaded")
                    }

                    val root = Paths.get("${cachePath.getPath()}/torrents/$infoHash")

                    val mp3Files = FileProcessor.getMP3Files(root)
                    Log.d("MusicDao", "DownloadFinishUseCase: mp3 files in $root: $mp3Files")

                    val songs =
                        mp3Files?.map {
                            SongEntity(
                                file = it.filename,
                                title = FileProcessor.getTitle(it),
                                artist = albumEntity.artist
                            )
                        } ?: listOf()

                    val cover = FileProcessor.getCoverArt(root)
                    val updatedAlbumEntity =
                        albumEntity.copy(
                            songs = songs,
                            cover = cover?.absolutePath,
                            root = root.toString(),
                            isDownloaded = true,
                            torrentPath = Paths.get("${cachePath.getPath()}/torrents/$infoHash.torrent").toString()
                        )

                    Log.d("MusicDao", "DownloadFinishUseCase: updated album with $updatedAlbumEntity")
                    database.dao.update(updatedAlbumEntity)
                }
            }
        }
    }

    fun magnetToInfoHash(magnet: String): String? {
        val mark = "magnet:?xt=urn:btih:"
        val start = magnet.indexOf(mark) + mark.length
        if (start == -1) return null
        return magnet.substring(20, start + 40)
    }

    /**
     * Generates a deterministic public key based on the input string.
     * This is used to create a unique public key for each artist.
     */
    fun getDeterministicSegwitAddress(input: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val seed = sha256.digest(input.toByteArray())
        val key = ECKey.fromPrivate(seed)
        val params = walletService.wallet().networkParameters
        return SegwitAddress.fromKey(params, key).toString()
    }
}
