import SwiftUI

struct ResultDisplay: View {
    let result: String?
    let error: String?
    let label: String
    var solscanHash: String? = nil

    var body: some View {
        if let result {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(label):")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(PhantomColors.successGreen)
                Text(result)
                    .font(.caption)
                    .monospaced()
                    .foregroundColor(.primary)
                    .textSelection(.enabled)
                if let solscanHash {
                    Link(destination: URL(string: "https://solscan.io/tx/\(solscanHash)")!) {
                        Text("View on Solscan")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(PhantomColors.explorerGreen, lineWidth: 1)
                            )
                            .foregroundColor(PhantomColors.explorerGreen)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .padding(.top, 4)
                }
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(PhantomColors.successGreen.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.top, 8)
        }

        if let error {
            Text(error)
                .font(.caption)
                .foregroundColor(PhantomColors.red)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(PhantomColors.red.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .padding(.top, 8)
        }
    }
}
