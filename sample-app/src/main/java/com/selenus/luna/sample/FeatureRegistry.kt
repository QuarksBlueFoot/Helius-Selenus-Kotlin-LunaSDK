package com.selenus.luna.sample

import com.selenus.luna.LunaHeliusClient
import kotlinx.serialization.json.*

data class FeatureDemo(
    val name: String,
    val category: String,
    val description: String,
    val action: suspend (LunaHeliusClient, (String) -> Unit) -> Unit
)

object FeatureRegistry {
    fun getFeatures(): List<FeatureDemo> {
        return listOf(
            // --- DAS API ---
            FeatureDemo("Get Asset", "DAS", "Fetch a single asset by ID") { client, log ->
                val asset = client.das.getAsset("F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk")
                val name = asset.result?.jsonObject?.get("content")?.jsonObject?.get("metadata")?.jsonObject?.get("name")?.jsonPrimitive?.content
                log("Asset Name: $name")
            },
            FeatureDemo("Get Assets By Owner", "DAS", "List assets for a wallet") { client, log ->
                val assets = client.das.getAssetsByOwner("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", page = 1, limit = 5)
                val total = assets.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Total Assets: $total")
            },
            FeatureDemo("Search Assets", "DAS", "Search assets by criteria") { client, log ->
                val search = client.das.searchAssets(mapOf("ownerAddress" to "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY"))
                val total = search.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Search Result Total: $total")
            },
            FeatureDemo("Get Asset Batch", "DAS", "Fetch multiple assets") { client, log ->
                val assets = client.das.getAssetBatch(listOf("F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk"))
                val count = assets.result?.jsonArray?.size
                log("Batch Count: $count")
            },
            FeatureDemo("Get Asset Proof", "DAS", "Get Merkle proof") { client, log ->
                // Example compressed asset ID (replace with valid one)
                val assetId = "F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk" 
                val proof = client.das.getAssetProof(assetId)
                log("Proof Root: ${proof.result?.jsonObject?.get("root")}")
            },
            FeatureDemo("Get Asset Proof Batch", "DAS", "Get multiple Merkle proofs") { client, log ->
                 val assetId = "F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk"
                 val proofs = client.das.getAssetProofBatch(listOf(assetId))
                 log("Batch Proofs: ${proofs.result?.jsonArray?.size}")
            },

            FeatureDemo("Get Assets By Authority", "DAS", "List assets by authority") { client, log ->
                val assets = client.das.getAssetsByAuthority("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", page = 1, limit = 5)
                val total = assets.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Assets by Authority: $total")
            },
            FeatureDemo("Get Assets By Creator", "DAS", "List assets by creator") { client, log ->
                val assets = client.das.getAssetsByCreator("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", page = 1, limit = 5)
                val total = assets.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Assets by Creator: $total")
            },
            FeatureDemo("Get Assets By Group", "DAS", "List assets by group") { client, log ->
                val assets = client.das.getAssetsByGroup("collection", "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", page = 1, limit = 5)
                val total = assets.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Assets by Group: $total")
            },
            FeatureDemo("Get NFT Editions", "DAS", "Get editions for master NFT") { client, log ->
                val editions = client.das.getNftEditions("F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk")
                val total = editions.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Editions: $total")
            },
            FeatureDemo("Get Token Accounts", "DAS", "Get token accounts") { client, log ->
                val accounts = client.das.getTokenAccounts(owner = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")
                val total = accounts.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Token Accounts: $total")
            },
            FeatureDemo("Get Signatures For Asset", "DAS", "Get asset history") { client, log ->
                val sigs = client.das.getSignaturesForAsset("F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk")
                val total = sigs.result?.jsonObject?.get("total")?.jsonPrimitive?.content
                log("Signatures: $total")
            },

            // --- RPC API ---
            FeatureDemo("Get Slot", "RPC", "Get current slot") { client, log ->
                val slot = client.rpcCall("getSlot", JsonArray(emptyList()))
                log("Current Slot: ${slot.result}")
            },
            FeatureDemo("Get Block Height", "RPC", "Get current block height") { client, log ->
                val height = client.rpcCall("getBlockHeight", JsonArray(emptyList()))
                log("Block Height: ${height.result}")
            },
            FeatureDemo("Get Version", "RPC", "Get node version") { client, log ->
                val version = client.rpcCall("getVersion", JsonArray(emptyList()))
                log("Version: ${version.result}")
            },
            FeatureDemo("Get Genesis Hash", "RPC", "Get genesis hash") { client, log ->
                val genesis = client.rpcCall("getGenesisHash", JsonArray(emptyList()))
                log("Genesis: ${genesis.result}")
            },
            FeatureDemo("Get Program Accounts V2", "RPC", "Enhanced getProgramAccounts") { client, log ->
                // Using Token Program ID
                val accounts = client.rpc.getProgramAccountsV2("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", limit = 5)
                log("Program Accounts: ${accounts.result}")
            },
            FeatureDemo("Get All Program Accounts", "RPC", "Auto-paginated program accounts") { client, log ->
                // Using a small program or limiting logic would be better, but for now we skip to avoid OOM
                log("Skipping: Can be very large and slow for demo")
            },

            FeatureDemo("Get Token Accounts By Owner V2", "RPC", "Enhanced getTokenAccounts") { client, log ->
                val accounts = client.rpc.getTokenAccountsByOwnerV2("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", limit = 5)
                log("Token Accounts V2: ${accounts.result}")
            },
            FeatureDemo("Get All Token Accounts By Owner", "RPC", "Auto-paginated token accounts") { client, log ->
                val accounts = client.rpc.getAllTokenAccountsByOwner("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")
                log("All Token Accounts: ${accounts.result}")
            },
            FeatureDemo("Get Transactions For Address", "RPC", "Get tx history") { client, log ->
                val txs = client.rpc.getTransactionsForAddress("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", mapOf("limit" to JsonPrimitive(5)))
                log("Transactions: ${txs.result}")
            },

            // --- Staking API ---
            FeatureDemo("Create Stake Transaction", "Staking", "Create stake tx") { client, log ->
                val tx = client.staking.createStakeTransaction("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", 1000000, "Vote111111111111111111111111111111111111111")
                log("Stake Tx: ${tx.result}")
            },
            FeatureDemo("Create Unstake Transaction", "Staking", "Create unstake tx") { client, log ->
                // Example stake account
                val tx = client.staking.createUnstakeTransaction("C5g2H2h4d6B7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2G")
                log("Unstake Tx: ${tx.result}")
            },
            FeatureDemo("Create Withdraw Transaction", "Staking", "Create withdraw tx") { client, log ->
                val tx = client.staking.createWithdrawTransaction("C5g2H2h4d6B7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2G", 500000)
                log("Withdraw Tx: ${tx.result}")
            },

            FeatureDemo("Get Stake Instructions", "Staking", "Get stake instructions") { client, log ->
                val inst = client.staking.getStakeInstructions("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", 1000000, "Vote111111111111111111111111111111111111111")
                log("Stake Instructions: ${inst.result}")
            },
            FeatureDemo("Get Unstake Instruction", "Staking", "Get unstake instruction") { client, log ->
                val inst = client.staking.getUnstakeInstruction("C5g2H2h4d6B7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2G")
                log("Unstake Inst: ${inst.result}")
            },
            FeatureDemo("Get Withdraw Instruction", "Staking", "Get withdraw instruction") { client, log ->
                val inst = client.staking.getWithdrawInstruction("C5g2H2h4d6B7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2G", 500000)
                log("Withdraw Inst: ${inst.result}")
            },
            FeatureDemo("Get Withdrawable Amount", "Staking", "Check withdrawable") { client, log ->
                val amount = client.staking.getWithdrawableAmount("C5g2H2h4d6B7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2G", true)
                log("Withdrawable: ${amount.result}")
            },

            FeatureDemo("Get Helius Stake Accounts", "Staking", "Get stake accounts") { client, log ->
                val accounts = client.staking.getHeliusStakeAccounts("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")
                val count = accounts.result?.jsonArray?.size
                log("Stake Accounts: $count")
            },

            // --- Transaction API ---
            FeatureDemo("Get Compute Units", "Transaction", "Estimate CU") { client, log ->
                // Example base64 transaction (empty/dummy for demo)
                val dummyTx = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
                val cu = client.tx.getComputeUnits(dummyTx)
                log("CU Estimate: ${cu.result}")
            },

            FeatureDemo("Broadcast Transaction", "Transaction", "Broadcast tx") { client, log ->
                // Requires a valid signed transaction string
                log("Skipping: Requires valid signed transaction")
            },
            FeatureDemo("Send Transaction", "Transaction", "Send tx") { client, log ->
                // Requires a valid signed transaction string
                log("Skipping: Requires valid signed transaction")
            },
            FeatureDemo("Poll Transaction Confirmation", "Transaction", "Poll tx") { client, log ->
                // Example signature (replace with real one to test)
                val sig = "25J2d5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3"
                try {
                    val status = client.tx.pollTransactionConfirmation(sig, timeoutMs = 5000)
                    log("Confirmation Status: ${status.result}")
                } catch (e: Exception) {
                    log("Poll failed (expected for dummy sig): ${e.message}")
                }
            },

            FeatureDemo("Create Smart Transaction", "Transaction", "Create smart tx (Example)") { client, log ->
                // This is a conceptual example of how to build a smart transaction using LunaSDK data.
                // 1. Get Priority Fee Estimate
                val feeEstimate = client.priority.getPriorityFeeEstimate(
                    priorityLevel = "High",
                    lookbackSlots = 100
                )
                val priorityFee = feeEstimate.result?.jsonObject?.get("priorityFeeEstimate")?.jsonPrimitive?.doubleOrNull ?: 0.0
                log("Step 1: Estimated Priority Fee: $priorityFee microLamports/CU")

                // 2. Build your transaction (using a library like sol4k or web3.js wrapper)
                // val tx = Transaction()
                // tx.add(SystemProgram.transfer(...))
                
                // 3. Add Compute Budget Instructions based on estimate
                // tx.add(ComputeBudgetProgram.setComputeUnitPrice(priorityFee.toLong()))
                
                // 4. Simulate/Get Compute Units to set limit
                // val simulatedUnits = client.tx.getComputeUnits(tx.serializeBase64())
                // val units = simulatedUnits.result?.jsonPrimitive?.longOrNull ?: 200_000
                // tx.add(ComputeBudgetProgram.setComputeUnitLimit(units))
                
                log("Step 2-4: (Requires Transaction Builder Library) Add SetComputeUnitPrice($priorityFee) and SetComputeUnitLimit instructions.")
                log("Smart Transaction logic is client-side composition of these Helius APIs.")
            },
            FeatureDemo("Send Smart Transaction", "Transaction", "Send smart tx (Example)") { client, log ->
                log("See 'Create Smart Transaction'. After building, sign and use client.tx.sendTransaction(base64Tx).")
            },
            FeatureDemo("Send Transaction With Sender", "Transaction", "Send via Jito (Example)") { client, log ->
                // 1. Get Jito Tip Floor
                val tipFloor = client.tx.getSenderTipFloor()
                val tipAmount = tipFloor.result?.jsonPrimitive?.doubleOrNull ?: 0.0
                log("Step 1: Jito Tip Floor: $tipAmount SOL")
                
                // 2. Add Tip Instruction
                val tipAccount = LunaHeliusClient.SENDER_TIP_ACCOUNTS.random()
                log("Step 2: Add Transfer instruction of $tipAmount SOL to $tipAccount")
                
                // 3. Sign Transaction
                // val signedTx = ...
                
                // 4. Send via Sender
                // val sig = client.tx.sendTransactionWithSender(signedTx, region = LunaHeliusClient.SenderRegion.US_EAST)
                log("Step 4: Call client.tx.sendTransactionWithSender(signedTx, region = ...)")
            },


            // --- Priority Fee API ---
            FeatureDemo("Get Priority Fee Estimate", "Priority", "Estimate fee") { client, log ->
                val fee = client.priority.getPriorityFeeEstimate("High")
                log("Fee Estimate: ${fee.result}")
            },

            // --- Enhanced API ---
            FeatureDemo("Get Enhanced Transactions", "Enhanced", "Get parsed txs") { client, log ->
                // Example signature
                val sig = "25J2d5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3s5q3"
                val txs = client.enhanced.getTransactions(listOf(sig))
                log("Enhanced Tx: ${txs.result}")
            },

            FeatureDemo("Get Enhanced Txs By Address", "Enhanced", "Get parsed txs by address") { client, log ->
                val txs = client.enhanced.getTransactionsByAddress("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY", limit = 5)
                val count = txs.result?.jsonArray?.size
                log("Enhanced Txs: $count")
            },

            // --- Webhooks API ---
            FeatureDemo("Create Webhook", "Webhooks", "Create webhook") { client, log ->
                val hook = client.webhooks.createWebhook(
                    webhookUrl = "https://example.com/webhook",
                    accountAddresses = listOf("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY"),
                    transactionTypes = listOf("Any")
                )
                log("Created Webhook ID: ${hook.result?.jsonObject?.get("webhookID")}")
            },

            FeatureDemo("Get All Webhooks", "Webhooks", "List webhooks") { client, log ->
                val hooks = client.webhooks.getAllWebhooks()
                val count = hooks.result?.jsonArray?.size
                log("Webhooks: $count")
            },
            FeatureDemo("Get Webhook By Id", "Webhooks", "Get webhook details") { client, log ->
                // Fetch first available webhook ID to demo
                val hooks = client.webhooks.getAllWebhooks()
                val firstId = hooks.result?.jsonArray?.firstOrNull()?.jsonObject?.get("webhookID")?.jsonPrimitive?.content
                if (firstId != null) {
                    val hook = client.webhooks.getWebhookById(firstId)
                    log("Webhook Details: ${hook.result}")
                } else {
                    log("No webhooks found to fetch.")
                }
            },
            FeatureDemo("Update Webhook", "Webhooks", "Update webhook") { client, log ->
                val hooks = client.webhooks.getAllWebhooks()
                val firstId = hooks.result?.jsonArray?.firstOrNull()?.jsonObject?.get("webhookID")?.jsonPrimitive?.content
                if (firstId != null) {
                    val update = client.webhooks.updateWebhook(firstId, mapOf("webhookUrl" to JsonPrimitive("https://example.com/updated")))
                    log("Updated Webhook: ${update.result}")
                } else {
                    log("No webhooks found to update.")
                }
            },
            FeatureDemo("Delete Webhook", "Webhooks", "Delete webhook") { client, log ->
                // Be careful with deletion in demos!
                log("Skipping: Deletion disabled for safety in demo.")
            },


            // --- ZK Compression ---
            FeatureDemo("Get Indexer Health", "ZK", "Check health") { client, log ->
                val health = client.zk.getIndexerHealth()
                log("Health: ${health.result}")
            },
            FeatureDemo("Get Latest Compression Signatures", "ZK", "Get signatures") { client, log ->
                val sigs = client.zk.getLatestCompressionSignatures(10)
                val count = sigs.result?.jsonArray?.size
                log("Signatures: $count")
            },

            // --- Standard RPC ---
            FeatureDemo("Get Balance", "Standard RPC", "Get SOL balance") { client, log ->
                val params = buildJsonArray { add(JsonPrimitive("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")) }
                val balance = client.rpcCall("getBalance", params)
                log("Balance: ${balance.result?.jsonObject?.get("value")}")
            },
            FeatureDemo("Get Supply", "Standard RPC", "Get token supply") { client, log ->
                val supply = client.rpcCall("getSupply", JsonArray(emptyList()))
                log("Supply: ${supply.result?.jsonObject?.get("value")?.jsonObject?.get("total")}")
            },
            FeatureDemo("Get Identity", "Standard RPC", "Get node identity") { client, log ->
                val identity = client.rpcCall("getIdentity", JsonArray(emptyList()))
                log("Identity: ${identity.result?.jsonObject?.get("identity")}")
            },
            FeatureDemo("Get Health", "Standard RPC", "Get node health") { client, log ->
                val health = client.rpcCall("getHealth", JsonArray(emptyList()))
                log("Health: ${health.result}")
            }
        )
    }
}
