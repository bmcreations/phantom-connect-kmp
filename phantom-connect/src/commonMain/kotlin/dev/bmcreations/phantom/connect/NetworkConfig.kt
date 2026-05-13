package dev.bmcreations.phantom.connect

/**
 * Per-network metadata matching upstream @phantom/constants NetworkConfig.
 */
data class NetworkConfig(
    val networkId: String,
    val name: String,
    val chain: Chain,
    val network: Network,
    val slip44: Int? = null,
    val chainId: Int? = null,
    val explorerTransactionUrl: String? = null,
    val explorerAddressUrl: String? = null,
)

object NetworkConfigs {
    private val configs = listOf(
        NetworkConfig(NetworkId.SOLANA_MAINNET, "Solana", Chain.Solana, Network.Mainnet, slip44 = 501, explorerTransactionUrl = "https://explorer.solana.com/tx/{value}", explorerAddressUrl = "https://explorer.solana.com/address/{value}"),
        NetworkConfig(NetworkId.SOLANA_DEVNET, "Solana Devnet", Chain.Solana, Network.Devnet, slip44 = 501, explorerTransactionUrl = "https://explorer.solana.com/tx/{value}?cluster=devnet", explorerAddressUrl = "https://explorer.solana.com/address/{value}?cluster=devnet"),
        NetworkConfig(NetworkId.SOLANA_TESTNET, "Solana Testnet", Chain.Solana, Network.Testnet, slip44 = 501),
        NetworkConfig(NetworkId.ETHEREUM_MAINNET, "Ethereum", Chain.Ethereum, Network.Mainnet, slip44 = 60, chainId = 1, explorerTransactionUrl = "https://etherscan.io/tx/{value}", explorerAddressUrl = "https://etherscan.io/address/{value}"),
        NetworkConfig(NetworkId.ETHEREUM_SEPOLIA, "Ethereum Sepolia", Chain.Ethereum, Network.Testnet, slip44 = 60, chainId = 11155111, explorerTransactionUrl = "https://sepolia.etherscan.io/tx/{value}", explorerAddressUrl = "https://sepolia.etherscan.io/address/{value}"),
        NetworkConfig(NetworkId.POLYGON_MAINNET, "Polygon", Chain.Polygon, Network.Mainnet, slip44 = 60, chainId = 137, explorerTransactionUrl = "https://polygonscan.com/tx/{value}", explorerAddressUrl = "https://polygonscan.com/address/{value}"),
        NetworkConfig(NetworkId.POLYGON_AMOY, "Polygon Amoy", Chain.Polygon, Network.Testnet, slip44 = 60, chainId = 80002),
        NetworkConfig(NetworkId.BASE_MAINNET, "Base", Chain.Base, Network.Mainnet, slip44 = 60, chainId = 8453, explorerTransactionUrl = "https://basescan.org/tx/{value}", explorerAddressUrl = "https://basescan.org/address/{value}"),
        NetworkConfig(NetworkId.BASE_SEPOLIA, "Base Sepolia", Chain.Base, Network.Testnet, slip44 = 60, chainId = 84532),
        NetworkConfig(NetworkId.ARBITRUM_MAINNET, "Arbitrum One", Chain.Arbitrum, Network.Mainnet, slip44 = 60, chainId = 42161, explorerTransactionUrl = "https://arbiscan.io/tx/{value}", explorerAddressUrl = "https://arbiscan.io/address/{value}"),
        NetworkConfig(NetworkId.ARBITRUM_SEPOLIA, "Arbitrum Sepolia", Chain.Arbitrum, Network.Testnet, slip44 = 60, chainId = 421614),
        NetworkConfig(NetworkId.MONAD_MAINNET, "Monad", Chain.Monad, Network.Mainnet, slip44 = 60, chainId = 143),
        NetworkConfig(NetworkId.MONAD_TESTNET, "Monad Testnet", Chain.Monad, Network.Testnet, slip44 = 60, chainId = 10143),
        NetworkConfig(NetworkId.BITCOIN_MAINNET, "Bitcoin", Chain.Bitcoin, Network.Mainnet, slip44 = 0, explorerTransactionUrl = "https://mempool.space/tx/{value}", explorerAddressUrl = "https://mempool.space/address/{value}"),
        NetworkConfig(NetworkId.BITCOIN_TESTNET, "Bitcoin Testnet", Chain.Bitcoin, Network.Testnet, slip44 = 0),
        NetworkConfig(NetworkId.SUI_MAINNET, "Sui", Chain.Sui, Network.Mainnet, slip44 = 784),
        NetworkConfig(NetworkId.SUI_TESTNET, "Sui Testnet", Chain.Sui, Network.Testnet, slip44 = 784),
        NetworkConfig(NetworkId.SUI_DEVNET, "Sui Devnet", Chain.Sui, Network.Devnet, slip44 = 784),
        NetworkConfig(NetworkId.HYPERCORE_MAINNET, "Hypercore", Chain.Hypercore, Network.Mainnet, slip44 = 60, chainId = 999),
        NetworkConfig(NetworkId.HYPERCORE_TESTNET, "Hypercore Testnet", Chain.Hypercore, Network.Testnet, slip44 = 60, chainId = 9999),
    )

    private val byNetworkId = configs.associateBy { it.networkId }

    fun get(networkId: String): NetworkConfig? = byNetworkId[networkId]

    fun getExplorerUrl(networkId: String, type: ExplorerUrlType, value: String): String? {
        val config = byNetworkId[networkId] ?: return null
        val template = when (type) {
            ExplorerUrlType.Transaction -> config.explorerTransactionUrl
            ExplorerUrlType.Address -> config.explorerAddressUrl
        } ?: return null
        return template.replace("{value}", value)
    }

    fun getSupportedNetworks(): List<NetworkConfig> = configs

    fun getNetworksByChain(chain: Chain): List<NetworkConfig> =
        configs.filter { it.chain == chain }

    fun chainIdToNetworkId(chainId: Int): String? =
        configs.firstOrNull { it.chainId == chainId }?.networkId

    fun networkIdToChainId(networkId: String): Int? =
        byNetworkId[networkId]?.chainId
}

enum class ExplorerUrlType { Transaction, Address }
