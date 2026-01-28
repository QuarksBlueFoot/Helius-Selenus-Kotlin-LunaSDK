# Luna NLP - Natural Language Transaction Builder

<p align="center">
  <img src="https://i.imgur.com/WYjuIcH.png" alt="Luna SDK" width="200"/>
</p>

> 🗣️ Build Solana transactions by typing in plain English

Luna NLP is a **deterministic, offline-capable** natural language parser for the [Luna SDK](../README.md). It converts human-readable commands into structured transaction intents—no AI, no API calls, instant response.

## Features

- **🚫 No AI/LLM** - Pure regex pattern matching, runs offline
- **⚡ Instant** - Sub-millisecond parsing
- **🔗 Domain Support** - `.sol` (Bonfida SNS) and `.skr` (SKR) domains
- **🪙 Token Resolution** - Jupiter token list + well-known tokens
- **💸 Transfers** - SOL and SPL token transfers
- **🔄 Swaps** - Jupiter-powered token swaps
- **📊 DAS Queries** - NFTs, assets, compressed assets
- **🥩 Staking** - Stake/unstake with various protocols
- **🔒 Privacy** - Privacy analysis integration
- **🌐 Webhooks** - Natural language webhook management

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("xyz.selenus:luna-nlp:5.3.0")
}
```

## Quick Start

```kotlin
import xyz.selenus.luna.nlp.NaturalLanguageBuilder
import xyz.selenus.luna.nlp.DefaultEntityResolver
import xyz.selenus.luna.nlp.ParseResult

// Create the resolver (handles domain/token lookups)
val resolver = DefaultEntityResolver()

// Create the NLP builder
val nlp = NaturalLanguageBuilder.create(resolver)

// Parse natural language
val result = nlp.parse("send 1 SOL to alice.sol")

when (result) {
    is ParseResult.Success -> {
        val intent = result.intent
        println("Intent: $intent")
        println("Confidence: ${result.confidence}")
    }
    is ParseResult.NeedsInfo -> {
        println("Missing: ${result.missing}")
        println("Suggestion: ${result.suggestion}")
    }
    is ParseResult.Unknown -> {
        println("Try these commands:")
        result.suggestions.forEach { println("  ${it.template}") }
    }
}
```

## Supported Commands

### Transfers

| Pattern | Example |
|---------|---------|
| `send {amount} SOL to {address}` | `send 1 SOL to alice.sol` |
| `transfer {amount} {token} to {address}` | `transfer 100 USDC to moonmanquark.skr` |
| `pay {address} {amount} SOL` | `pay bob.sol 0.5 SOL` |

### Swaps

| Pattern | Example |
|---------|---------|
| `swap {amount} {token} for {token}` | `swap 100 USDC for SOL` |
| `exchange {amount} {token} to {token}` | `exchange 1 SOL to BONK` |
| `buy {amount} {token} with {token}` | `buy 1000 BONK with SOL` |
| `sell {amount} {token} for {token}` | `sell 50 USDC for SOL` |

### Staking

| Pattern | Example |
|---------|---------|
| `stake {amount} SOL` | `stake 10 SOL` |
| `stake {amount} SOL with {protocol}` | `stake 5 SOL with marinade` |
| `unstake {amount} SOL` | `unstake 2 SOL` |
| `claim rewards` | `claim my staking rewards` |

### Balance & Assets

| Pattern | Example |
|---------|---------|
| `check balance` | `check my balance` |
| `check balance of {address}` | `check balance of alice.sol` |
| `get {token} balance` | `get my USDC balance` |
| `show my NFTs` | `show my NFTs` |
| `list assets of {address}` | `list assets of moonmanquark.skr` |

### Domain Resolution

| Pattern | Example |
|---------|---------|
| `resolve {domain}` | `resolve alice.sol` |
| `what is {domain}` | `what is moonmanquark.skr` |
| `reverse lookup {address}` | `reverse lookup F42Zov...` |

### Privacy

| Pattern | Example |
|---------|---------|
| `analyze privacy` | `analyze my privacy` |
| `check privacy of {address}` | `check privacy of alice.sol` |

### Webhooks

| Pattern | Example |
|---------|---------|
| `create webhook for {address}` | `create webhook for alice.sol` |
| `monitor {address}` | `monitor moonmanquark.skr` |

## Configuration

```kotlin
val nlp = NaturalLanguageBuilder.create(resolver) {
    defaultSlippageBps = 50        // Default slippage 0.5%
    defaultWallet = "myWallet.sol" // Default wallet for "check my balance"
}
```

## Domain Support

Luna NLP supports multiple domain systems:

| Domain | Provider | Example |
|--------|----------|---------|
| `.sol` | Bonfida SNS | `alice.sol` |
| `.skr` | SKR Domains | `moonmanquark.skr` |

The resolver automatically detects the domain type and queries the appropriate API.

## Parse Results

The parser returns one of four result types:

```kotlin
sealed class ParseResult {
    // Successfully parsed, high confidence
    data class Success(
        val intent: TransactionIntent,
        val confidence: Double,
        val rawInput: String
    )
    
    // Multiple interpretations possible
    data class Ambiguous(
        val possibleIntents: List<TransactionIntent>,
        val clarificationPrompt: String
    )
    
    // Partially understood, needs more info
    data class NeedsInfo(
        val intentType: IntentType,
        val missing: List<String>,
        val partial: Map<String, String>,
        val suggestion: String
    )
    
    // Could not understand
    data class Unknown(
        val input: String,
        val suggestions: List<CommandSuggestion>
    )
}
```

## Intent Types

The NLP module generates the following transaction intents:

| Intent | Description |
|--------|-------------|
| `TransferSol` | Transfer SOL to an address |
| `TransferToken` | Transfer SPL tokens |
| `Swap` | Swap tokens via Jupiter |
| `SwapExactOut` | Swap with exact output amount |
| `Stake` | Stake SOL with a validator/protocol |
| `Unstake` | Unstake SOL |
| `ClaimRewards` | Claim staking rewards |
| `GetBalance` | Query SOL balance |
| `GetTokenBalance` | Query token balance |
| `GetAssets` | Get DAS assets |
| `ResolveDomain` | Resolve .sol/.skr domain |
| `ReverseLookup` | Get domain from address |
| `AnalyzePrivacy` | Run privacy analysis |
| `CreateWebhook` | Create a webhook |
| `NftTransfer` | Transfer NFT |

## Amount Parsing

Supports various number formats:

| Format | Parsed Value |
|--------|-------------|
| `1` | 1 |
| `1.5` | 1.5 |
| `1,000` | 1000 |
| `1k` | 1,000 |
| `1m` | 1,000,000 |
| `1b` | 1,000,000,000 |

## Token Resolution

The NLP module includes a well-known token cache for popular tokens:

- SOL, USDC, USDT, BONK, WIF, JTO, JUP, RAY, ORCA
- PYTH, W, RENDER, HNT, MOBILE, IOT, SAMO, FIDA

Unknown tokens can be specified by mint address.

## Example: Building a Chatbot

```kotlin
class SolanaChatbot {
    private val resolver = DefaultEntityResolver()
    private val nlp = NaturalLanguageBuilder.create(resolver) {
        defaultWallet = userWallet
    }
    private var context = NlpContext()
    
    suspend fun processMessage(message: String): String {
        val result = nlp.parseWithContext(message, context)
        
        return when (result) {
            is ParseResult.Success -> {
                // Update context
                context = updateContext(result.intent)
                
                // Execute or preview
                executeIntent(result.intent)
            }
            is ParseResult.NeedsInfo -> {
                result.suggestion
            }
            is ParseResult.Unknown -> {
                "I didn't understand that. Try:\n" +
                    result.suggestions.joinToString("\n") { "• ${it.template}" }
            }
        }
    }
}
```

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                    User Input                           │
│              "send 1 SOL to alice.sol"                 │
└────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────┐
│                  NaturalLanguageBuilder                 │
│                                                         │
│  ┌─────────────────┐  ┌──────────────────────────────┐ │
│  │ Pattern Matcher │  │     Entity Resolver          │ │
│  │ (Regex-based)   │──│  • SNS (.sol) API           │ │
│  │                 │  │  • SKR (.skr) API           │ │
│  │ • Transfers     │  │  • Jupiter token list        │ │
│  │ • Swaps         │  │  • Well-known tokens cache   │ │
│  │ • Staking       │  └──────────────────────────────┘ │
│  │ • Queries       │                                   │
│  └─────────────────┘                                   │
└────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────┐
│                    ParseResult                          │
│                                                         │
│  Success ─► TransactionIntent (ready to execute)       │
│  NeedsInfo ─► Missing fields + suggestion              │
│  Unknown ─► Command suggestions                        │
└────────────────────────────────────────────────────────┘
```

## Related Modules

- [luna-core](../luna-core) - Core primitives
- [luna-rpc](../luna-rpc) - RPC operations
- [luna-das](../luna-das) - Digital Asset Standard
- [luna-jupiter](../luna-jupiter) - Jupiter DEX integration
- [luna-privacy](../luna-privacy) - Privacy analysis

---

<p align="center">
  Built with 💜 by <a href="https://x.com/moonmanquark">@moonmanquark</a> & Selenus
</p>

<p align="center">
  <sub>
    <strong>Donations:</strong> solanadevdao.sol • F42ZovBoRJZU4av5MiESVwJWnEx8ZQVFkc1RM29zMxNT
  </sub>
</p>
