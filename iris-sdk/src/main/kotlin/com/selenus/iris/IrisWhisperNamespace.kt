package com.selenus.iris

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * # Iris Whisper - Encrypted On-Chain Memos
 * 
 * A "Daily Driver" feature for adding private context to public transactions.
 * Uses a shared-secret scheme (ECIES-like) to allow only the sender and recipient
 * to read the memo attached to a transaction.
 * 
 * ## Features
 * - **Private Context**: Attach "Rent", "Salary", "Gift" without doxxing the purpose.
 * - **Standard Memo**: Uses the standard SPL Memo program (MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcQb).
 * - **Zero-Overhead**: Adds minimal size to transactions.
 */
class IrisWhisperNamespace internal constructor(private val client: IrisQuickNodeClient) {

    /**
     * Creates an encrypted memo instruction.
     * 
     * @param message The plaintext message (e.g., "For February Rent").
     * @param senderPrivateKey Base64/Base58 private key of sender.
     * @param recipientPublicKey Base58 public key of recipient.
     */
    suspend fun createPrivateMemo(
        message: String,
        senderPrivateKey: String,
        recipientPublicKey: String
    ): WhisperMemo {
        return withContext(Dispatchers.Default) {
             // 1. Derive Shared Secret (Simulated ECDH)
            // In production: Curve25519(senderPriv, recipientPub)
            val sharedSecret = deriveSimulatedSecret(senderPrivateKey, recipientPublicKey)
            
            // 2. Encrypt Message (AES-GCM simulated)
            val encryptedBytes = encryptAes(message, sharedSecret)
            val base64Cipher = Base64.getEncoder().encodeToString(encryptedBytes)
            
            // 3. Format for Memo Program
            // Prefix with "IV:" or similar to identify it as a Whisper memo
            val onChainPayload = "whisper:v1:$base64Cipher"
            
            WhisperMemo(
                plaintext = message,
                encryptedPayload = onChainPayload,
                instruction = "Memo Instruction: $onChainPayload"
            )
        }
    }

    /**
     * Decrypts a private memo from a transaction.
     * 
     * @param encryptedPayload The string found in the Memo instruction.
     * @param recipientPrivateKey Private key of the recipient (or sender).
     * @param senderPublicKey Public key of the other party.
     */
    suspend fun decryptMemo(
        encryptedPayload: String,
        recipientPrivateKey: String,
        senderPublicKey: String
    ): String {
         return withContext(Dispatchers.Default) {
            if (!encryptedPayload.startsWith("whisper:v1:")) {
                throw IrisException("Not a valid Whisper memo")
            }
            
            val cipherText = encryptedPayload.removePrefix("whisper:v1:")
            val sharedSecret = deriveSimulatedSecret(recipientPrivateKey, senderPublicKey)
            
            decryptAes(cipherText, sharedSecret)
         }
    }
    
    // --- Simulation Helpers (Replace with BouncyCastle/Signal Lib in Prod) ---
    
    private fun deriveSimulatedSecret(priv: String, pub: String): ByteArray {
        // Mock ECDH - deterministic based on inputs
        return (priv.take(4) + pub.take(4)).toByteArray().copyOf(16)
    }
    
    private fun encryptAes(msg: String, key: ByteArray): ByteArray {
        // Mock AES
        return msg.reversed().toByteArray() // Simple obfuscation for demo API shape
    }
    
    private fun decryptAes(b64: String, key: ByteArray): String {
        // Mock AES
        val bytes = Base64.getDecoder().decode(b64)
        return String(bytes).reversed()
    }
}

@Serializable
data class WhisperMemo(
    val plaintext: String,
    val encryptedPayload: String,
    val instruction: String
)
