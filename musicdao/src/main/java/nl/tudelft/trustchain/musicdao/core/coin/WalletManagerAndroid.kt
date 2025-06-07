package nl.tudelft.trustchain.musicdao.core.coin

import android.content.Context
import java.io.File

/**
 * Singleton class for WalletManager which also sets-up Android specific things.
 */
object WalletManagerAndroid { // TODO: Clean up Thread usage.
    private val walletManagers = mutableMapOf<String, WalletManager>()
    private val isRunning = mutableMapOf<String, Boolean>()

    fun getInstance(walletId: String): WalletManager {
        return walletManagers[walletId]
            ?: throw IllegalStateException("WalletManager with ID '$walletId' is not initialized")
    }

    class Factory(
        private val context: Context
    ) {
        private var configuration: WalletManagerConfiguration? = null
        private var walletId: String? = null

        fun setConfiguration(configuration: WalletManagerConfiguration): Factory {
            this.configuration = configuration
            return this
        }

        fun setWalletId(walletId: String): Factory {
            this.walletId = walletId
            return this
        }

        fun init(): WalletManager {
            val configuration =
                configuration
                    ?: throw IllegalStateException("Configuration is not set")
            val walletId = this.walletId
                ?: throw IllegalStateException("Wallet ID is not set")

            val walletDir = File(context.filesDir, "wallet_$walletId").apply {
                if (!exists()) mkdirs()
            }

            val walletManager =
                WalletManager(
                    configuration,
                    walletDir,
                    configuration.key,
                    configuration.addressPrivateKeyPair
                )

            walletManagers[walletId] = walletManager
            isRunning[walletId] = true

            return walletManager
        }
    }

    fun isInitialized(walletId: String): Boolean {
        return walletManagers.containsKey(walletId)
    }

    /**
     * Stops and resets the current wallet manager.
     * This method will block the thread until the kit has been shut down.
     */
    fun close() {
        walletManagers.values.forEach { it.kit.stopAsync().awaitTerminated() }
        walletManagers.clear()
        isRunning.clear()
    }
}
