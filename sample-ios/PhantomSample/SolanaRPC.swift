import Foundation

enum SolanaRPC {
    private static let rpcURL = URL(string: "https://api.mainnet-beta.solana.com")!

    static func getBalance(address: String) async throws -> Int64 {
        let body: [String: Any] = [
            "jsonrpc": "2.0",
            "id": 1,
            "method": "getBalance",
            "params": [address],
        ]
        let result = try await call(body: body)
        guard let value = (result["result"] as? [String: Any])?["value"] as? Int64 else {
            return 0
        }
        return value
    }

    static func buildSelfTransferTransaction(address: String) async throws -> String {
        // Get latest blockhash
        let blockhashBody: [String: Any] = [
            "jsonrpc": "2.0",
            "id": 1,
            "method": "getLatestBlockhash",
            "params": [[:] as [String: Any]],
        ]
        let blockhashResult = try await call(body: blockhashBody)
        guard let blockhash = ((blockhashResult["result"] as? [String: Any])?["value"] as? [String: Any])?["blockhash"] as? String else {
            throw SolanaError.missingBlockhash
        }

        // Build a minimal self-transfer transaction (0.000001 SOL)
        let transaction = try buildTransferTransaction(
            from: address,
            to: address,
            lamports: 1000,
            blockhash: blockhash
        )
        return transaction
    }

    // MARK: - Private

    private static func call(body: [String: Any]) async throws -> [String: Any] {
        var request = URLRequest(url: rpcURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, _) = try await URLSession.shared.data(for: request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SolanaError.invalidResponse
        }
        return json
    }

    /// Build a Solana transfer transaction manually (system program transfer instruction).
    /// Returns base64-encoded serialized transaction.
    private static func buildTransferTransaction(
        from: String,
        to: String,
        lamports: UInt64,
        blockhash: String
    ) throws -> String {
        let fromKey = try Base58.decode(from)
        let toKey = try Base58.decode(to)
        let blockhashBytes = try Base58.decode(blockhash)

        guard fromKey.count == 32, toKey.count == 32, blockhashBytes.count == 32 else {
            throw SolanaError.invalidKey
        }

        // System Program ID (all zeros)
        let systemProgramId = [UInt8](repeating: 0, count: 32)

        var message = Data()

        // Header: num_required_signatures=1, num_readonly_signed=0, num_readonly_unsigned=1
        message.append(contentsOf: [1, 0, 1])

        // Account keys (compact array)
        // 2 unique keys if from==to, but we still need system program
        let isSelfTransfer = fromKey == toKey
        if isSelfTransfer {
            message.append(2) // compact-u16: 2 accounts
            message.append(contentsOf: fromKey)
            message.append(contentsOf: systemProgramId)
        } else {
            message.append(3) // compact-u16: 3 accounts
            message.append(contentsOf: fromKey)
            message.append(contentsOf: toKey)
            message.append(contentsOf: systemProgramId)
        }

        // Recent blockhash
        message.append(contentsOf: blockhashBytes)

        // Instructions (compact array: 1 instruction)
        message.append(1) // compact-u16: 1 instruction

        // System Program Transfer instruction
        if isSelfTransfer {
            message.append(1) // program_id_index = 1 (system program)
            message.append(2) // num accounts = 2
            message.append(0) // from account index
            message.append(0) // to account index (same as from)
        } else {
            message.append(2) // program_id_index = 2 (system program)
            message.append(2) // num accounts = 2
            message.append(0) // from account index
            message.append(1) // to account index
        }

        // Instruction data: transfer = instruction index 2 (u32 LE) + lamports (u64 LE)
        var instructionData = Data()
        var transferIndex: UInt32 = 2
        instructionData.append(contentsOf: withUnsafeBytes(of: &transferIndex) { Array($0) })
        var lamportsLE = lamports
        instructionData.append(contentsOf: withUnsafeBytes(of: &lamportsLE) { Array($0) })

        message.append(UInt8(instructionData.count)) // data length
        message.append(instructionData)

        // Build transaction: num_signatures (compact) + empty signature + message
        var transaction = Data()
        transaction.append(1) // compact-u16: 1 signature
        transaction.append(contentsOf: [UInt8](repeating: 0, count: 64)) // placeholder signature
        transaction.append(message)

        return transaction.base64EncodedString()
    }
}

enum SolanaError: LocalizedError {
    case missingBlockhash
    case invalidResponse
    case invalidKey

    var errorDescription: String? {
        switch self {
        case .missingBlockhash: return "Failed to get latest blockhash"
        case .invalidResponse: return "Invalid RPC response"
        case .invalidKey: return "Invalid Solana key"
        }
    }
}

// MARK: - Base58

enum Base58 {
    private static let alphabet = Array("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

    static func decode(_ string: String) throws -> [UInt8] {
        let chars = Array(string)
        var bytes = [UInt8]()

        for char in chars {
            guard let index = alphabet.firstIndex(of: char) else {
                throw SolanaError.invalidKey
            }
            var carry = index
            for j in stride(from: bytes.count - 1, through: 0, by: -1) {
                carry += Int(bytes[j]) * 58
                bytes[j] = UInt8(carry & 0xFF)
                carry >>= 8
            }
            while carry > 0 {
                bytes.insert(UInt8(carry & 0xFF), at: 0)
                carry >>= 8
            }
        }

        // Leading zeros
        for char in chars {
            if char == "1" {
                bytes.insert(0, at: 0)
            } else {
                break
            }
        }

        return bytes
    }
}
