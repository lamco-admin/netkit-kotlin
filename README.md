# NetKit

**Kotlin Multiplatform networking toolkit for WiFi analysis, diagnostics, and optimization**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-purple.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-JVM-orange.svg)]()

## Overview

NetKit is a comprehensive Kotlin Multiplatform library suite for WiFi network analysis, diagnostics, and optimization. Originally developed as the core networking engine for the WiFi Intelligence Android application, NetKit has been extracted and open-sourced to benefit the broader networking and wireless community.

## Features

- **Zero Android Dependencies**: Pure Kotlin code that runs on any JVM platform
- **Comprehensive WiFi Analysis**: RF modeling, signal analysis, channel optimization
- **Network Diagnostics**: Advanced troubleshooting and performance analysis
- **Security Analysis**: WPA/WPA2/WPA3 security auditing and recommendations
- **Intelligent Recommendations**: ML-based network optimization suggestions
- **Export Tools**: Generate reports in multiple formats
- **High Test Coverage**: ~90% code coverage with comprehensive test suites
- **Full Documentation**: Dokka-generated API documentation

## Modules

NetKit is organized into seven focused modules:

### `core`
Foundation module containing data models, RF analysis, and IE parsing.
- WiFi data models (AP, ScanResult, Channel, etc.)
- RF propagation and signal modeling
- Information Element (IE) parser
- Network topology mapping
- Zero external dependencies

### `diagnostics`
Network diagnostics and troubleshooting tools.
- Connection quality analysis
- Performance metrics
- Issue detection and categorization
- Diagnostic report generation

### `wifi-optimizer`
Channel and configuration optimization.
- Channel selection algorithms
- Interference analysis
- Band steering recommendations
- Power optimization

### `analytics`
Network analytics and trend analysis.
- Historical data processing
- Statistical analysis
- Trend detection
- Performance benchmarking

### `security`
Security analysis and recommendations.
- WPA/WPA2/WPA3 analysis
- Security posture assessment
- Vulnerability detection
- Encryption recommendations

### `intelligence`
ML-based intelligent recommendations.
- Network optimization suggestions
- Anomaly detection
- Predictive analysis
- Context-aware recommendations

### `export`
Report generation and data export.
- Multiple export formats
- Customizable report templates
- Data serialization
- Visualization support

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Core module (required)
    implementation("io.lamco.netkit:core:1.0.1")

    // Optional feature modules
    implementation("io.lamco.netkit:diagnostics:1.0.1")
    implementation("io.lamco.netkit:wifi-optimizer:1.0.1")
    implementation("io.lamco.netkit:analytics:1.0.1")
    implementation("io.lamco.netkit:security:1.0.1")
    implementation("io.lamco.netkit:intelligence:1.0.1")
    implementation("io.lamco.netkit:export:1.0.1")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.lamco.netkit:core:1.0.1'
    implementation 'io.lamco.netkit:diagnostics:1.0.1'
    // ... other modules as needed
}
```

### Maven

```xml
<dependency>
    <groupId>io.lamco.netkit</groupId>
    <artifactId>core</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Quick Start

```kotlin
import io.lamco.netkit.model.network.*
import io.lamco.netkit.model.security.*
import io.lamco.netkit.rf.*
import io.lamco.netkit.diagnostics.*

// Create access point from scan data
val accessPoint = AccessPoint(
    bssid = "00:11:22:33:44:55",
    ssid = "MyNetwork",
    frequency = 2437,
    rssi = -65,
    securityTypes = listOf(SecurityType.WPA3_PERSONAL)
)

// Analyze RF environment
val channelAnalyzer = ChannelAnalyzer()
val analysis = channelAnalyzer.analyzeChannel(accessPoint.channel)

// Run network diagnostics
val diagnosticEngine = DiagnosticEngine()
val issues = diagnosticEngine.analyzeSignalQuality(accessPoint)

// Get optimization recommendations
val optimizer = ChannelOptimizer()
val recommendations = optimizer.findOptimalChannel(listOf(accessPoint))
```

## Documentation

API documentation is generated using Dokka and can be built locally:

```bash
./gradlew dokkaHtmlAll
# Documentation output: build/dokka/html/index.html
```

Each module contains inline KDoc documentation describing public APIs and usage patterns.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/lamco-admin/netkit-kotlin.git
cd netkit-kotlin

# Build all modules
./gradlew buildAll

# Run tests
./gradlew testAll

# Generate documentation
./gradlew dokkaHtmlAll
```

## Requirements

- Kotlin 2.2.21 or higher
- JDK 11 or higher
- Gradle 8.x (wrapper included)

## Versioning

NetKit follows [Semantic Versioning](https://semver.org/).

Current version: **1.0.1**

## License

NetKit is licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2025 Lamco Development

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contributing

Contributions are welcome! Please open an issue to discuss proposed changes before submitting pull requests.

## Support

- [Issue Tracker](https://github.com/lamco-admin/netkit-kotlin/issues)
- [Discussions](https://github.com/lamco-admin/netkit-kotlin/discussions)
- Email: greg@lamco.io

## Acknowledgments

NetKit was extracted from the [WiFi Intelligence](https://play.google.com/store/apps/details?id=com.lamco.wifiintelligence) Android application, currently in closed beta testing. Special thanks to all beta testers who helped refine these libraries.

## Related Projects

- [WiFi Intelligence](https://github.com/lamco-admin/wifiintelligence) - Android app powered by NetKit

---

**Version**: 1.0.1
