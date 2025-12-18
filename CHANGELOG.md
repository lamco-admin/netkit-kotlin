# Changelog

All notable changes to NetKit will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2025-12-18

### Added

#### Core Module
- **IEEE 802.11 Information Element Parser**: Complete parsing of RSN, HT, VHT, HE, EHT capabilities
- **WiFi Security Analysis**: Comprehensive security rating system (0-100 scale) with WPA3, SAE-PK detection
- **RF Analysis Suite**: SNR calculation, distance estimation (RSSI/RTT), capacity estimation, channel analysis
- **Topology Analysis**: Network clustering, roaming detection, coverage analysis, site survey tools
- **Router Vendor Detection**: Database of 100+ vendors identified by OUI
- **Configurable Logging**: Platform-independent logger with console, SLF4J, and custom implementations

#### Diagnostics Module
- **Network Diagnostics**: Ping, DNS, traceroute, bandwidth testing
- **Bufferbloat Testing**: Latency under load analysis with quality scoring
- **Multi-stream Bandwidth**: Parallel connection testing (4-stream, 8-stream)
- **Enhanced DNS Testing**: DNSSEC support, cache effectiveness, hijacking detection
- **VoIP Quality Analysis**: MOS scoring, jitter, packet loss metrics
- **Comprehensive Reporting**: Health scores, diagnostic orchestration, markdown/JSON export

#### WiFi Optimizer Module
- **Auto Channel Planning**: DFS-aware channel selection with regulatory compliance
- **Tx Power Optimization**: Coverage-based power recommendations
- **Load Balancing**: Client distribution analysis across APs
- **Client Steering**: Band steering and roaming recommendations
- **QoS Analysis**: Traffic prioritization assessment
- **Mesh Network Analysis**: Backhaul optimization, topology visualization

#### Security Module
- **Security Analyzer**: Multi-factor security assessment (encryption, authentication, PMF, WPS)
- **Configuration Advisor**: Rule-based recommendations for network hardening
- **Cipher Analysis**: Detection of deprecated ciphers (TKIP, WEP)
- **WPS Risk Assessment**: PIN attack vulnerability detection
- **PMF Analysis**: Protected Management Frame status checking
- **Pro Mode Reporting**: Technical security details for advanced users

#### Intelligence Module
- **Context-Aware Recommendations**: Network type-specific advice (home, enterprise, public)
- **Configuration Generator**: Template-based router setup
- **Interactive Guidance**: Step-by-step troubleshooting workflows
- **Best Practices Database**: Standards-based recommendations (IEEE, WiFi Alliance)
- **Troubleshooting Engine**: Root cause analysis, symptom matching, solution ranking
- **Vendor-Specific Knowledge**: Router-specific optimization tips

#### Analytics Module
- **Time Series Analysis**: Trend detection, forecasting, seasonality analysis
- **Statistical Analysis**: Distribution characterization, outlier detection
- **Correlation Analysis**: Network metric relationships
- **Comparative Analysis**: Before/after, benchmark comparisons
- **Visualization**: Heatmaps, charts, topology diagrams
- **Data Aggregation**: Time-windowed metrics, percentile calculations

#### Export Module
- **Multi-format Export**: CSV, JSON, Markdown, HTML, PDF
- **Report Templates**: Professional network analysis reports
- **Compliance Checking**: Data validation before export
- **Batch Export**: Queued export processing
- **Template Engine**: Customizable report layouts

### Technical Details

- **Language**: Kotlin 2.2.21
- **Platforms**: JVM, Android (Kotlin Multiplatform)
- **Build System**: Gradle 9.2.1 with Kotlin DSL
- **Testing**: 276 unit tests with JUnit 5, Kotest, MockK
- **Documentation**: Comprehensive KDoc on all public APIs
- **Code Quality**: ktlint formatting, manual best practices review

### Performance

- Zero dependencies in core module
- Minimal dependency footprint across all modules
- Efficient algorithms for real-time RF analysis
- Memory-conscious data structures for large network scans

### Standards Compliance

- IEEE 802.11-2020 (WiFi 7/802.11be)
- IEEE 802.11ax (WiFi 6/6E)
- IEEE 802.11ac (WiFi 5)
- WPA3 Specification (WiFi Alliance)
- RFC 1393 (Traceroute)
- RFC 8110 (OWE - Enhanced Open)

## [Unreleased]

### Planned Features
- iOS platform support (Kotlin Multiplatform Native)
- Real-time monitoring capabilities
- Historical trend database
- Advanced ML-based anomaly detection
- WebAssembly support for browser-based analysis

---

**Legend**:
- `Added` - New features
- `Changed` - Changes in existing functionality
- `Deprecated` - Soon-to-be removed features
- `Removed` - Removed features
- `Fixed` - Bug fixes
- `Security` - Security improvements
