# WiFi Intelligence - Security Advisor Library

**Version**: 1.0.0
**Status**: Phase 3 - Batch 1 Complete (Security Data Models)
**Module**: `libs/security-advisor`

## Purpose

Comprehensive security analysis and advisory engine for WiFi networks. Provides:

- **Security scoring** - Per-BSS security assessment (0-100 scale)
- **Issue detection** - 14+ security vulnerability types
- **WPS risk analysis** - WiFi Protected Setup vulnerability assessment
- **Configuration quality** - Network-wide configuration evaluation
- **Actionable recommendations** - Plain-language security advice

## Architecture

### Package Structure

```
libs/security-advisor/
├── model/          # Security data models (Batch 1 - COMPLETE)
│   ├── SecurityIssue.kt
│   ├── SecurityScorePerBss.kt
│   ├── WpsRisk.kt
│   └── ConfigurationQuality.kt
├── analyzer/       # Security analyzers (Batch 2 - Planned)
├── advisor/        # Recommendation engines (Batch 3 - Planned)
└── rules/          # Rule engine (Batch 3 - Planned)
```

## Phase 3 - Batch 1: Security Data Models (✅ COMPLETE)

### Implemented Components

#### 1. SecurityIssue (14 issue types)

Sealed class hierarchy for security vulnerabilities:

**Cryptographic Weaknesses**:
- `LegacyCipher(cipher)` - WEP, TKIP detection
- `WepInUse` - WEP encryption (CRITICAL)
- `TkipInUse` - TKIP cipher (HIGH)

**Management Frame Protection**:
- `PmfDisabledOnProtectedNetwork(authType)` - Missing PMF (802.11w)
- `WeakGroupMgmtCipher` - Weak BIP cipher

**Open Networks**:
- `OpenNetworkWithoutOwe` - Unencrypted open network
- `OweTransitionWithOpenSideVisible(ssid)` - OWE transition mode

**WPA3 & Enterprise**:
- `SuiteBMissingForHighSecurityClaim` - Missing Suite-B 192-bit

**Transitional Modes**:
- `TransitionalMode(from, to)` - WPA2/WPA3, Open/OWE, etc.

**WPS Vulnerabilities**:
- `WpsPinEnabled` - WPS PIN brute force risk
- `WpsUnknownOrRiskyMode` - Unknown WPS configuration

**Roaming & Multi-AP**:
- `MissingRoamingOptimizations` - Missing 802.11k/v/r

**Configuration**:
- `InconsistentSecurityAcrossAps(ssid, count)` - Mixed security configs
- `DeprecatedAuthType(authType)` - WEP, WPA1 usage

#### 2. SecurityScorePerBss

Per-access-point security scoring with multi-dimensional analysis:

**Score Components**:
- **Cipher strength** (0.0-1.0) - Encryption quality
- **Management protection** (0.0-1.0) - PMF (802.11w) effectiveness
- **Overall security** (0.0-1.0) - Composite score

**Weighting**:
- Authentication type: 40%
- Cipher strength: 35%
- Management protection: 25%

**Security Levels**:
- EXCELLENT (90%+) - WPA3 or strong WPA2 + PMF
- GOOD (70-89%) - WPA2 with strong ciphers
- FAIR (50-69%) - Acceptable but not ideal
- WEAK (30-49%) - Deprecated protocols
- INSECURE (0-29%) - Critical vulnerabilities

**Features**:
- Automatic issue detection from `SecurityFingerprint`
- Factory methods for common configurations
- Detailed reporting with recommendations
- Issue severity counting

#### 3. WpsRisk

WiFi Protected Setup vulnerability assessment:

**WpsInfo Data Model**:
- Config methods (PIN, push-button, NFC, etc.)
- WPS state (configured, not configured)
- Lock status
- Device information

**WpsRiskScore**:
- Risk scoring (0.0-1.0 scale)
- Issue detection (PIN support, unlocked state, etc.)
- Risk levels: LOW, MEDIUM, HIGH, CRITICAL

**Risk Algorithm**:
- PIN unlocked configured: 1.0 (CRITICAL)
- PIN unlocked not configured: 0.8 (HIGH)
- PIN locked: 0.6 (MEDIUM-HIGH)
- Push-button only unlocked: 0.4 (MEDIUM)
- Push-button locked: 0.2 (LOW)
- WPS disabled: 0.0 (NO RISK)

**WpsConfigMethod**:
- 13 config methods (USB, Ethernet, Label, Display, NFC, Push-button, etc.)
- Bitmask parsing from WPS IE
- PIN vs non-PIN detection

#### 4. ConfigurationQuality

Network-wide configuration assessment:

**ConfigurationReport**:
- Per-SSID quality analysis
- Multi-AP consistency checking
- Component scoring:
  - Security consistency (40%)
  - Roaming capability (30%)
  - Channel planning (30%)

**Quality Levels**:
- EXCELLENT (90%+) - Best practices followed
- GOOD (75-89%) - Well configured
- FAIR (60-74%) - Some improvements needed
- POOR (40-59%) - Significant issues
- CRITICAL (<40%) - Immediate attention required

**ConfigurationIssue Types**:
- `InconsistentSecurity(ssid, count)` - Mixed security across APs
- `MissingRoamingFeatures(ssid, apCount, coverage)` - Limited 802.11k/v/r
- `PoorChannelPlanning(quality, issue)` - Channel interference
- `MixedWiFiGenerations(hasLegacy802_11b)` - Legacy device bottleneck
- `ExcessiveApOverlap(apCount, avgOverlap)` - Too many APs
- `InfoNote(message)` - Informational messages

## Usage Examples

### Security Scoring

```kotlin
import com.lamco.wifiintelligence.security.model.*
import com.lamco.wifiintelligence.model.topology.*

// Create security fingerprint
val fingerprint = SecurityFingerprint(
    authType = AuthType.WPA2_PSK,
    cipherSet = setOf(CipherSuite.CCMP),
    pmfRequired = false,
    transitionMode = null
)

// Generate security score
val score = SecurityScorePerBss.fromFingerprint(
    bssid = "00:11:22:33:44:55",
    ssid = "MyNetwork",
    fingerprint = fingerprint
)

// Check results
println("Security Level: ${score.securityLevel}")
println("Overall Score: ${(score.overallSecurityScore * 100).toInt()}%")
println("Issues: ${score.totalIssueCount}")

score.issues.forEach { issue ->
    println("[${issue.severity}] ${issue.shortDescription}")
    println("  → ${issue.recommendation}")
}
```

### WPS Risk Assessment

```kotlin
// Create WPS info
val wpsInfo = WpsInfo(
    configMethods = setOf(WpsConfigMethod.LABEL, WpsConfigMethod.PUSH_BUTTON),
    wpsState = WpsState.CONFIGURED,
    locked = false,
    deviceName = "Router-5G",
    manufacturer = "Netgear",
    modelName = "R7000",
    version = "2.0"
)

// Calculate risk
val riskScore = WpsRiskScore.fromWpsInfo("00:11:22:33:44:55", wpsInfo)

println("WPS Risk: ${riskScore.riskLevel} (${(riskScore.riskScore * 100).toInt()}%)")
println("Issues:")
riskScore.issues.forEach { issue ->
    println("  - ${issue.description}")
}

if (riskScore.isCriticalRisk) {
    println("⚠️ CRITICAL: Disable WPS immediately!")
}
```

### Configuration Quality

```kotlin
// Create configuration report
val report = ConfigurationReport(
    ssid = "CorporateWiFi",
    apCount = 8,
    securityConsistencyScore = 0.95,
    roamingCapabilityScore = 0.85,
    channelPlanScore = 0.90,
    overallQualityScore = 0.90,
    issues = listOf(
        ConfigurationIssue.MissingRoamingFeatures("CorporateWiFi", 8, 0.5)
    )
)

println(report.configurationSummary)
println(report.detailedReport)

// Check for issues
if (report.hasCriticalIssues) {
    println("⚠️ ${report.criticalIssueCount} critical issue(s) require immediate attention")
}
```

## Standards Compliance

### IEEE 802.11 Standards
- **IEEE 802.11-2020** - WiFi security specifications
- **IEEE 802.11w-2009** - Protected Management Frames (PMF)
- **IEEE 802.11k-2008** - Radio Resource Management
- **IEEE 802.11r-2008** - Fast BSS Transition
- **IEEE 802.11v-2011** - Wireless Network Management

### Security Specifications
- **WPA3 Specification** (Wi-Fi Alliance)
  - SAE (Simultaneous Authentication of Equals)
  - SAE-PK (Public Key)
  - Enterprise 192-bit mode
- **RFC 8110** - Opportunistic Wireless Encryption (OWE)
- **WPS Specification** - WiFi Protected Setup

### Government Standards
- **NIST SP 800-153** - Guidelines for Securing Wireless LANs
- **NIST SP 800-97** - Establishing Wireless Robust Security Networks
- **CNSA Suite** - NSA Commercial National Security Algorithm Suite

## Testing

**Test Coverage**: 206 comprehensive tests (343% of 60+ requirement)

### Test Files

1. **SecurityIssueTest.kt** (30 tests)
   - All 14 issue types
   - Severity validation
   - Description completeness
   - Recommendation quality

2. **SecurityScorePerBssTest.kt** (52 tests)
   - Score calculation algorithms
   - Cipher strength scoring
   - Management protection scoring
   - Issue detection from fingerprints
   - Security level categorization
   - Edge cases and validation

3. **WpsRiskTest.kt** (68 tests)
   - WpsInfo data model
   - Config method parsing
   - Risk score calculation
   - Issue detection
   - Risk level categorization
   - All WPS scenarios

4. **ConfigurationQualityTest.kt** (56 tests)
   - Configuration report creation
   - Quality level categorization
   - Score calculations
   - Issue detection
   - Multi-AP vs single-AP handling
   - All issue types

### Running Tests

```bash
# Run all tests
./gradlew :libs:security-advisor:test

# Run with coverage
./gradlew :libs:security-advisor:test jacocoTestReport

# Run specific test class
./gradlew :libs:security-advisor:test --tests SecurityScorePerBssTest
```

## Dependencies

### Module Dependencies
- `libs:core-model` - Domain models (AuthType, CipherSuite, SecurityFingerprint)
- `libs:core-ieparser` - Information Element parsing (future)
- `libs:core-rf` - RF metrics (future integration)

### External Dependencies
- Kotlin stdlib 1.9+
- Kotlin coroutines 1.8.0 (for future async operations)

### Test Dependencies
- JUnit 5.10.1
- MockK 1.13.8
- Kotest 5.8.0

## API Documentation

Generate full API docs with Dokka:

```bash
./gradlew :libs:security-advisor:dokkaHtml
```

Output: `libs/security-advisor/build/dokka/html/`

## Roadmap

### Phase 3 - Batch 2: Security Analyzers (~6 hours)
- [ ] SecurityAnalyzer.kt - Overall security posture
- [ ] WpsAnalyzer.kt - WPS vulnerability detection
- [ ] CipherAnalyzer.kt - Cipher suite analysis
- [ ] PmfAnalyzer.kt - Management frame protection analyzer
- [ ] ConfigAnalyzer.kt - Configuration consistency checker
- [ ] **Target**: 100+ tests, ~1,200 LOC

### Phase 3 - Batch 3: Advisor & Rule Engine (~8 hours)
- [ ] RuleEngine.kt - Pattern-based rule matching
- [ ] SecurityAdvisor.kt - Plain-language recommendations
- [ ] ConfigurationAdvisor.kt - Network optimization advice
- [ ] RiskPrioritizer.kt - Issue prioritization
- [ ] ProModeReporter.kt - Professional detailed reports
- [ ] **Target**: 120+ tests, ~1,500 LOC

### Total Phase 3 Target
- **280+ tests** (currently: 206)
- **~4,500 LOC production** (currently: ~1,200)
- **100% KDoc coverage** ✅
- **Zero Android dependencies** ✅
- **Platform-independent** ✅

## Quality Metrics

### Batch 1 Achievements
- ✅ **4 production files** (~1,200 LOC)
- ✅ **4 test files** (~900 LOC)
- ✅ **206 comprehensive tests** (343% of requirement)
- ✅ **100% KDoc coverage**
- ✅ **Zero Android dependencies**
- ✅ **Full implementations** (zero TODOs)

### Code Quality
- Immutable data classes
- Sealed class hierarchies
- Comprehensive validation
- Type-safe enumerations
- Null-safe APIs

## License

Copyright © 2025 WiFi Intelligence
Internal research library - Not for public distribution

## Changelog

### v1.0.0 - Phase 3 Batch 1 (2025-11-22)
- ✅ SecurityIssue sealed class (14 issue types)
- ✅ SecurityScorePerBss (multi-dimensional scoring)
- ✅ WpsRisk (WPS vulnerability assessment)
- ✅ ConfigurationQuality (network-wide analysis)
- ✅ 206 comprehensive tests
- ✅ Complete KDoc documentation

---

**Next**: Phase 3 Batch 2 - Security Analyzers
