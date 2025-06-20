package nl.tudelft.trustchain.musicdao

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.Room
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.parser.GsonParser
import nl.tudelft.trustchain.musicdao.core.cache.parser.Converters
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.torrent.fileProcessing.DownloadFinishUseCase
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig.Companion.DEFAULT_FAUCET_ENDPOINT
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig.Companion.DEFAULT_FILE_PREFIX
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig.Companion.DEFAULT_NETWORK_PARAMS
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig.Companion.DEFAULT_REGTEST_BOOTSTRAP_IP
import nl.tudelft.trustchain.musicdao.core.wallet.WalletConfig.Companion.DEFAULT_REGTEST_BOOTSTRAP_PORT
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.swig.settings_pack
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.common.util.InMemoryCache
import nl.tudelft.trustchain.common.util.PreferenceHelper
import nl.tudelft.trustchain.musicdao.core.coin.*
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_IS_NODE_ENABLED
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_NODE_BITCOIN_ADDRESS
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class HiltModules {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext applicationContext: Context
    ): CacheDatabase {
        return Room.databaseBuilder(
            applicationContext,
            CacheDatabase::class.java,
            "musicdao-database"
        ).fallbackToDestructiveMigration()
            .addTypeConverter(Converters(GsonParser(Gson())))
            .build()
    }

    @Provides
    @Singleton
    fun provideServerDatabase(
        @ApplicationContext applicationContext: Context
    ): ServerDatabase {
        return Room.databaseBuilder(
            applicationContext,
            ServerDatabase::class.java,
            "musicdao-server-database"
        ).fallbackToDestructiveMigration()
            .addTypeConverter(nl.tudelft.trustchain.musicdao.core.node.persistence.parser.Converters(GsonParser(Gson())))
            .build()
    }

    @Provides
    @Singleton
    fun createSessionParams(
        @ApplicationContext applicationContext: Context
    ): SessionManager {
        val settingsPack = SettingsPack()

        val port =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getString("musicdao_port", "10129")
                ?.toIntOrNull()
        if (port != null) {
            val interfaceFormat = "0.0.0.0:%1\$d,[::]:%1\$d"
            settingsPack.listenInterfaces(String.format(interfaceFormat, port))
        }

        settingsPack.setBoolean(
            settings_pack.bool_types.announce_to_all_trackers.swigValue(),
            true
        )
        settingsPack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        settingsPack.setBoolean(
            settings_pack.bool_types.listen_system_port_fallback.swigValue(),
            false
        )
        settingsPack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false)
        settingsPack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)
        settingsPack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false)

        // Disable Local Discovery
        settingsPack.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false)

        // Increase the limits of auto-managed (queuing) torrents
        settingsPack.setInteger(settings_pack.int_types.active_downloads.swigValue(), 1000)
        settingsPack.setInteger(settings_pack.int_types.active_seeds.swigValue(), 1000)
        settingsPack.setInteger(settings_pack.int_types.active_checking.swigValue(), 1000)
        settingsPack.setInteger(settings_pack.int_types.active_limit.swigValue(), 1000)

        return SessionManager().apply {
            start(SessionParams(settingsPack))
        }
    }

    @Provides
    @Singleton
    fun musicCommunity(): MusicCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("MusicCommunity is not configured")
    }

    @Provides
    @Singleton
    fun path(
        @ApplicationContext applicationContext: Context
    ): CachePath {
        return CachePath(applicationContext)
    }

    @Provides
    @Singleton
    fun downloadFinishUseCase(
        database: CacheDatabase,
        cachePath: CachePath,
        walletService: WalletService,
        @ApplicationContext applicationContext: Context
    ): DownloadFinishUseCase {
        return DownloadFinishUseCase(database = database, cachePath = cachePath, walletService = walletService, context = applicationContext)
    }

    @Provides
    @Singleton
    fun provideWalletService(
        @ApplicationContext applicationContext: Context,
        walletManager: WalletManager
    ): WalletService {
        return WalletService(
            WalletConfig(
                networkParams = DEFAULT_NETWORK_PARAMS,
                filePrefix = DEFAULT_FILE_PREFIX,
                cacheDir = Paths.get("${applicationContext.cacheDir}").toFile(),
                regtestFaucetEndPoint = DEFAULT_FAUCET_ENDPOINT,
                regtestBootstrapIp = DEFAULT_REGTEST_BOOTSTRAP_IP,
                regtestBootstrapPort = DEFAULT_REGTEST_BOOTSTRAP_PORT
            ),
            walletManager.kit
        )
    }

    @Provides
    @Singleton
    @Named("payoutWallet")
    fun providePayoutWalletService(
        @ApplicationContext applicationContext: Context,
        @Named("payoutWalletManager")
        walletManager: WalletManager
    ): WalletService {
        val walletService =  WalletService(
            WalletConfig(
                networkParams = DEFAULT_NETWORK_PARAMS,
                filePrefix = DEFAULT_FILE_PREFIX + "payout_",
                cacheDir = Paths.get("${applicationContext.cacheDir}").toFile(),
                regtestFaucetEndPoint = DEFAULT_FAUCET_ENDPOINT,
                regtestBootstrapIp = DEFAULT_REGTEST_BOOTSTRAP_IP,
                regtestBootstrapPort = DEFAULT_REGTEST_BOOTSTRAP_PORT
            ),
            walletManager.kit
        )

        return walletService
    }

    @Provides
    @Singleton
    fun provideWalletManager(
        @ApplicationContext context: Context
    ): WalletManager {
        val walletId = "user_wallet"
        Log.d("MVDAO", "INITIATING DAO MODULE.")
        if (WalletManagerAndroid.isInitialized(walletId)) {
            Log.d("MVDAO", "DAO MODULE ALREADY INITIALIZED, SKIPPING")
            return WalletManagerAndroid.getInstance(walletId)
        }
        val params = BitcoinNetworkOptions.REG_TEST

        val config =
            WalletManagerConfiguration(
                params,
                null,
                null
            )

        WalletManagerAndroid.Factory(context)
            .setConfiguration(config)
            .setWalletId(walletId)
            .init()

        Log.d("MVDAO", "User wallet manager: ${WalletManagerAndroid.getInstance(walletId)}")

        return WalletManagerAndroid.getInstance(walletId)
    }

    @Provides
    @Singleton
    @Named("payoutWalletManager")
    fun providePayoutWalletManager(
        @ApplicationContext context: Context
    ): WalletManager {
        val walletId = "payout_wallet"
        Log.d("MVDAO", "INITIATING DAO MODULE.")
        if (WalletManagerAndroid.isInitialized(walletId)) {
            Log.d("MVDAO", "DAO MODULE ALREADY INITIALIZED, SKIPPING")
            return WalletManagerAndroid.getInstance(walletId)
        }
        val params = BitcoinNetworkOptions.REG_TEST

        val config =
            WalletManagerConfiguration(
                params,
                null,
                null
            )

        WalletManagerAndroid.Factory(context)
            .setConfiguration(config)
            .setWalletId(walletId)
            .init()

        Log.d("MVDAO", "Payout node wallet manager: ${WalletManagerAndroid.getInstance(walletId)}")

        return WalletManagerAndroid.getInstance(walletId)
    }


}

class CachePath(val applicationContext: Context) {
    fun getPath(): Path? {
        return Paths.get("${applicationContext.cacheDir}")
    }
}
