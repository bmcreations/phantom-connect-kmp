import SwiftUI
import PhantomConnectSDK

struct WalletInfoSection: View {
    let session: PhantomWalletSession

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Wallet ID:")
                .font(.caption)
                .fontWeight(.bold)
            Text(session.walletId)
                .font(.caption)
                .monospaced()
                .lineLimit(1)
                .truncationMode(.middle)
                .textSelection(.enabled)

            if !session.addresses.isEmpty {
                Spacer().frame(height: 4)
                Text("Addresses:")
                    .font(.caption)
                    .fontWeight(.bold)

                ForEach(session.addresses, id: \.address) { addr in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(addr.chain.uppercased()):")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(PhantomColors.purple)
                        Text(addr.address)
                            .font(.caption)
                            .monospaced()
                            .lineLimit(1)
                            .truncationMode(.middle)
                            .textSelection(.enabled)
                    }
                    .padding(.leading, 8)
                    .padding(.top, 4)
                }
            }
        }
    }
}
