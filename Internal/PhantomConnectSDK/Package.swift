// swift-tools-version: 5.9
import PackageDescription

let packageName = "PhantomConnectSDK"
let sharedPackageName = "PhantomConnectKMP"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        )
    ],
    targets: [
        .target(
            name: packageName,
            dependencies: [.target(name: sharedPackageName)],
            path: "./Sources"
        ),
        .binaryTarget(
            name: sharedPackageName,
            path: "../../phantom-connect/build/XCFrameworks/debug/\(sharedPackageName).xcframework"
        ),
    ]
)
