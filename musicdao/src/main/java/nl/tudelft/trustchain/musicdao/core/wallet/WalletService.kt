package nl.tudelft.trustchain.musicdao.core.wallet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.musicdao.core.coin.CoinUtil
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.util.*

class WalletService(val config: WalletConfig, private val app: WalletAppKit) {
    private var started = false
    private var percentageSynced = 0

    val userTransactions: MutableStateFlow<List<UserWalletTransaction>> = MutableStateFlow(listOf())
    val onSetupCompletedListeners = mutableListOf<() -> Unit>()

    fun addOnSetupCompletedListener(listener: () -> Unit) {
        onSetupCompletedListeners.add(listener)
    }

    init {
        app.setDownloadListener(
            object : DownloadProgressTracker() {
                override fun progress(
                    pct: Double,
                    blocksSoFar: Int,
                    date: Date?
                ) {
                    super.progress(pct, blocksSoFar, date)
                    val percentage = pct.toInt()
                    percentageSynced = percentage
                    Log.i("MusicDao2", "Progress: $percentage")
                }

                override fun doneDownload() {
                    super.doneDownload()
                    percentageSynced = 100
                    Log.d("MusicDao2", "Download Complete!")
                    Log.d("MusicDao2", "Balance: ${app.wallet().balance}")
                }
            }
        )
        started = true
    }

    fun wallet(): Wallet {
        return app.wallet()
    }

    fun isStarted(): Boolean {
        return started && app.wallet() != null
    }

    /**
     * Convert an amount of coins represented by a user input string, and then send it
     * @param coinsAmount the amount of coins to send, as a string, such as "5", "0.5"
     * @param publicKey the public key address of the cryptocurrency wallet to send the funds to
     */
    fun sendCoins(
        publicKey: String,
        coinsAmount: String
    ): String? {
        Log.d("MusicDao", "Wallet (1): sending $coinsAmount to $publicKey")

        val coins: BigDecimal =
            try {
                BigDecimal(coinsAmount.toDouble())
            } catch (e: NumberFormatException) {
                Log.d("MusicDao", "Wallet (2): failed to parse $coinsAmount")
                null
            } ?: return null

        val satoshiAmount = (coins * SATS_PER_BITCOIN).toLong()

        val targetAddress: Address =
            try {
                Address.fromString(config.networkParams, publicKey)
            } catch (e: Exception) {
                Log.d("MusicDao", "Wallet (3): failed to parse $publicKey")
                null
            } ?: return null

        val sendRequest = SendRequest.to(targetAddress, Coin.valueOf(satoshiAmount))
        val feePerKb = CoinUtil.calculateFeeWithPriority(config.networkParams, CoinUtil.TxPriority.MEDIUM_PRIORITY)
        sendRequest.feePerKb = Coin.valueOf(feePerKb)

        return try {
            val result = app.wallet().sendCoins(sendRequest)
            Log.d("MusicDao", "Wallet (2): successfully sent $coinsAmount to $publicKey")
            result.tx.txId.toString()
        } catch (e: Exception) {
            Log.d("MusicDao", "Wallet (3): failed sending $coinsAmount to $publicKey")
            null
        }
    }

    fun sendCoinsMulti(addressAmount: Map<String, Float>): String? {
        val tx = Transaction(config.networkParams)
        val outputs = mutableListOf<Pair<Address, Long>>()

        for ((addressStr, amount) in addressAmount) {
            try {
                val address = try {
                    Address.fromString(config.networkParams, addressStr)
                } catch (e: Exception) {
                    SegwitAddress.fromBech32(config.networkParams, addressStr)
                }
                val satoshis = (amount.toBigDecimal() * SATS_PER_BITCOIN).toLong()
                outputs.add(address to satoshis)
            } catch (e: Exception) {
                Log.w("MusicDao", "Invalid address: $addressStr")
            }
        }

        if (outputs.isEmpty()) return null

        outputs.forEach { (address, satoshis) ->
            tx.addOutput(Coin.valueOf(satoshis), address)
        }

        try {
            val sendRequest = SendRequest.forTx(tx)
            val feePerKb = CoinUtil.calculateFeeWithPriority(config.networkParams, CoinUtil.TxPriority.MEDIUM_PRIORITY)
            sendRequest.feePerKb = Coin.valueOf(feePerKb)
            sendRequest.ensureMinRequiredFee = true

            app.wallet().completeTx(sendRequest)

            val actualTx = sendRequest.tx
            val fee = actualTx.inputSum.subtract(actualTx.outputSum)

            val totalOriginal = outputs.sumOf { it.second }
            val feeLong = fee.value

            if (feeLong > totalOriginal) {
                Log.e("MusicDao", "Fee exceeds total amount.")
                return null
            }

            val adjustedTx = Transaction(config.networkParams)
            outputs.forEach { (address, originalAmount) ->
                val adjusted = ((originalAmount.toDouble() / totalOriginal) * (totalOriginal - feeLong)).toLong()
                adjustedTx.addOutput(Coin.valueOf(adjusted), address)
            }

            val adjustedRequest = SendRequest.forTx(adjustedTx)
            adjustedRequest.feePerKb = Coin.valueOf(feePerKb)
            adjustedRequest.ensureMinRequiredFee = true

            val result = app.wallet().sendCoins(adjustedRequest)
            Log.i("MusicDao", "Transaction sent: ${result.tx.txId}")
            return result.tx.txId.toString()

        } catch (e: Exception) {
            Log.e("MusicDao", "Error sending multi-output transaction", e)
            return null
        }
    }

    /**
     * Query the faucet to the default protocol address
     * @return whether request was successfully or not
     */
    suspend fun defaultFaucetRequest(): Boolean {
        return requestFaucet(protocolAddress().toString())
    }

    /**
     * Query the bitcoin faucet for some starter bitcoins
     * @param address the address to send the coins to
     * @return whether request was successfully or not
     */
    private suspend fun requestFaucet(address: String): Boolean {
        Log.d("MusicDao", "requestFaucet (1): $address")
        val obj = URL("${config.regtestFaucetEndPoint}/addBTC?address=$address")

        return withContext(Dispatchers.IO) {
            try {
                val con: InputStream? = obj.openStream()
                con?.close()
                Log.d(
                    "MusicDao",
                    "requestFaucet (2): $address using ${config.regtestFaucetEndPoint}/addBTC?address=$address"
                )
                true
            } catch (exception: IOException) {
                exception.printStackTrace()
                Log.d(
                    "MusicDao",
                    "requestFaucet failed (3): $address using ${config.regtestFaucetEndPoint}/addBTC?address=$address"
                )
                Log.d("MusicDao", "requestFaucet failed (4): $exception")
                false
            }
        }
    }

    fun walletStatus(): String {
        return app.state().name
    }

    fun percentageSynced(): Int {
        return percentageSynced
    }

    /**
     * @return default address used for all interactions on chain
     */
    fun protocolAddress(): Address {
        return app.wallet().issuedReceiveAddresses[0]
    }

    fun confirmedBalance(): Coin? {
        return try {
            app.wallet().balance
        } catch (e: java.lang.Exception) {
            null
        }
    }

    fun walletTransactions(): List<UserWalletTransaction> {
        return app.wallet().walletTransactions.map {
            UserWalletTransaction(
                transaction = it.transaction,
                value = it.transaction.getValue(app.wallet()),
                date = it.transaction.updateTime
            )
        }.sortedByDescending { it.date }
    }

    fun setWalletReceiveListener() {
        userTransactions.value = walletTransactions()
        app.wallet().addCoinsReceivedEventListener { _, _, _, _ ->
            userTransactions.value = walletTransactions()
        }
    }

    fun estimatedBalance(): String? {
        return try {
            app.wallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString()
        } catch (e: java.lang.Exception) {
            null
        }
    }

    /**
     * Sign a message using the wallet's current receive key.
     * This proves ownership of the associated Bitcoin address.
     *
     * @param message The message to be signed.
     * @return The signed message (Base64-encoded), or null if signing fails.
     */
    fun signMessage(message: String): String? {
        return try {
            val key = app.wallet().currentReceiveKey()
            key.signMessage(message)
        } catch (e: Exception) {
            Log.e("MusicDao", "signMessage: Failed to sign message", e)
            null
        }
    }

    companion object {
        val SATS_PER_BITCOIN = BigDecimal(100_000_000)
    }
}

data class UserWalletTransaction(
    val transaction: Transaction,
    val value: Coin,
    val date: Date
)
