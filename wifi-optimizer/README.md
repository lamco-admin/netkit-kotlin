# WiFi Intelligence - Network Optimization Library

**Version**: 1.0.0
**Module**: `libs/network-optimizer`
**Dependencies**: `core-model`, `core-rf`, `topology`

## Purpose

The Network Optimization Library provides automated network optimization and advanced feature analysis for WiFi networks. It delivers actionable configuration recommendations for:

- **Channel Planning**: Automated channel selection and optimization
- **TX Power Optimization**: Balance coverage and interference
- **QoS/WMM Analysis**: Quality of Service and airtime fairness
- **Client Steering**: Band and AP steering recommendations
- **Load Balancing**: Multi-AP load distribution analysis
- **Mesh Networks**: Mesh topology and backhaul optimization
- **Enterprise Features**: VLAN, 802.1X, guest network analysis

## Architecture

### Package Structure

```
com.lamco.wifiintelligence.optimizer/
├── model/
│   ├── ChannelPlanningConstraints.kt    # Channel planning inputs
│   ├── CoverageGoals.kt                 # Coverage optimization targets
│   ├── QosGoals.kt                      # QoS objectives
│   ├── MeshTopology.kt                  # Mesh network models
│   └── EnterpriseConfig.kt              # Enterprise feature models
├── algorithm/
│   ├── AutoChannelPlanner.kt            # Channel allocation algorithm
│   ├── TxPowerOptimizer.kt              # Power level optimization
│   ├── QosAnalyzer.kt                   # WMM and airtime analysis
│   ├── ClientSteeringAdvisor.kt         # Band/AP steering
│   ├── LoadBalancingAnalyzer.kt         # AP load distribution
│   └── MeshNetworkAnalyzer.kt           # Mesh-specific analysis
├── analyzer/
│   ├── EnterpriseAnalyzer.kt            # VLAN, 802.1X, guest networks
│   └── ConfigurationValidator.kt        # Best practice validation
└── engine/
    ├── ConfigurationRecommendationEngine.kt  # Unified recommendations
    └── OptimizationOrchestrator.kt           # Coordinate all optimizers
```

### Core Components

#### 1. AutoChannelPlanner

Automated channel planning for optimal RF performance:
- Multi-AP channel allocation
- DFS channel viability assessment
- Channel width optimization
- Co-channel and adjacent channel interference minimization

**Algorithm**:
1. Score all channels for each AP (interference, utilization, DFS risk)
2. Optimize globally for multi-AP (minimum separation, load balancing)
3. Validate plan (regulatory compliance, overlap prevention)

**Output**: `ChannelPlan` with assignments and improvement scores

#### 2. TxPowerOptimizer

TX power optimization for coverage and interference balance:
- Coverage-driven power calculation
- Interference-constrained optimization
- Energy efficiency considerations
- Multi-AP coordination

**Approach**:
- Use RF metrics (RSSI, distance estimation) from core-rf
- Model coverage area with path loss models
- Calculate interference zones
- Optimize for coverage goals while minimizing interference

**Output**: `PowerOptimizationResult` with per-AP recommendations

#### 3. QosAnalyzer

QoS and WMM (WiFi Multimedia) analysis:
- WMM effectiveness assessment
- Airtime fairness analysis
- Client prioritization recommendations
- Traffic profile optimization

**Features**:
- WMM queue parameter validation
- Airtime hog detection (Gini coefficient)
- Traffic class analysis (voice, video, data, background)
- QoS improvement recommendations

**Output**: `WmmAnalysisResult` and `AirtimeFairnessResult`

#### 4. ClientSteeringAdvisor

Client steering recommendations:
- Band steering (2.4 GHz → 5/6 GHz)
- AP steering (load balancing)
- Steering effectiveness analysis
- Dual-band client optimization

**Scoring Factors**:
- RSSI differential
- Band capabilities
- AP load
- Expected throughput improvement

**Output**: `BandSteeringRecommendation` and `ApSteeringRecommendation`

#### 5. MeshNetworkAnalyzer

Mesh network analysis and optimization:
- Backhaul quality assessment (wired vs. wireless)
- Mesh loop detection
- Hop count optimization
- Self-healing capability assessment

**Features**:
- Backhaul type detection (Ethernet, wireless, dedicated radio)
- Link quality scoring
- Bottleneck identification
- Topology recommendations

**Output**: `BackhaulAnalysisResult` and `MeshLoop` detection

#### 6. EnterpriseAnalyzer

Enterprise feature analysis:
- VLAN segmentation validation
- 802.1X authentication assessment
- Guest network isolation
- Captive portal performance

**Note**: Analysis based on observable behavior, not deep configuration access

## Usage Examples

### Channel Planning

```kotlin
import com.lamco.wifiintelligence.optimizer.algorithm.AutoChannelPlanner
import com.lamco.wifiintelligence.optimizer.model.*

val planner = AutoChannelPlanner()

val constraints = ChannelPlanningConstraints(
    band = WiFiBand.BAND_5GHZ,
    supportsDfs = true,
    preferredWidths = listOf(ChannelWidth.MHZ_80, ChannelWidth.MHZ_40),
    regulatoryDomain = RegulatoryDomain.FCC
)

val plan = planner.planChannels(apClusters, constraints)

println("Channel Plan Score: ${plan.score}")
println("Improvement: +${plan.improvementVsCurrent}%")
plan.assignments.forEach { (bssid, assignment) ->
    println("$bssid → Channel ${assignment.channel} (${assignment.width})")
    println("  Rationale: ${assignment.rationale}")
}
```

### TX Power Optimization

```kotlin
import com.lamco.wifiintelligence.optimizer.algorithm.TxPowerOptimizer

val optimizer = TxPowerOptimizer()

val coverageGoals = CoverageGoals(
    minRssi = -70,
    targetRssi = -60,
    maxInterference = 0.3
)

val result = optimizer.optimizePower(apClusters, coverageGoals, currentPower)

result.recommendations.forEach { (bssid, rec) ->
    println("$bssid: ${rec.currentPower} → ${rec.recommendedPower} dBm")
    println("  ${rec.rationale}")
    println("  Impact: ${rec.impact.coverageChange}, ${rec.impact.interferenceChange}")
}
```

### QoS Analysis

```kotlin
import com.lamco.wifiintelligence.optimizer.algorithm.QosAnalyzer

val qosAnalyzer = QosAnalyzer()

val wmmResult = qosAnalyzer.analyzeWmm(wmmConfig, trafficProfile)

if (!wmmResult.isEffective) {
    println("WMM Issues Found:")
    wmmResult.issues.forEach { issue ->
        when (issue) {
            is WmmIssue.QueueMisconfigured ->
                println("  - ${issue.queue}: ${issue.problem}")
            is WmmIssue.WmmDisabled ->
                println("  - WMM is disabled")
            // etc.
        }
    }
    println("\nRecommendations:")
    wmmResult.recommendations.forEach { println("  • $it") }
}
```

### Client Steering

```kotlin
import com.lamco.wifiintelligence.optimizer.algorithm.ClientSteeringAdvisor

val advisor = ClientSteeringAdvisor()

// Band steering
val bandRec = advisor.recommendBandSteering(
    client = clientCapabilities,
    currentBand = WiFiBand.BAND_2GHZ,
    availableBands = listOf(WiFiBand.BAND_5GHZ, WiFiBand.BAND_6GHZ)
)

if (bandRec.shouldSteer) {
    println("Recommend steering to ${bandRec.targetBand}")
    println("Expected: ${bandRec.expectedImprovement}")
}

// AP steering
val apRec = advisor.recommendApSteering(client, currentAp, availableAps)

if (apRec.shouldSteer) {
    println("Recommend steering to ${apRec.targetBssid}")
    println("Load reduction: ${apRec.expectedLoadReduction}%")
    println("Signal improvement: +${apRec.signalImprovement} dB")
}
```

### Mesh Network Analysis

```kotlin
import com.lamco.wifiintelligence.optimizer.algorithm.MeshNetworkAnalyzer

val meshAnalyzer = MeshNetworkAnalyzer()

val backhaulResult = meshAnalyzer.analyzeBackhaul(meshTopology)

println("Backhaul Quality: ${backhaulResult.quality}")
println("\nBottlenecks:")
backhaulResult.bottlenecks.forEach { bottleneck ->
    println("  - ${bottleneck.description}")
}
println("\nRecommendations:")
backhaulResult.recommendations.forEach { println("  • $it") }
```

## Key Algorithms

### Channel Planning Scoring

```
Score = 100 - penalties

Penalties:
- Co-channel interference: -20 per neighbor
- Adjacent channel interference: -10 per neighbor (±1, ±2 channels)
- DFS risk: -15 (HIGH), -10 (MEDIUM), -5 (LOW)
- Channel utilization: -(utilization * 20)
```

### TX Power Optimization

```
1. Calculate power for coverage: P_coverage = P_target - PL(d_target)
2. Calculate interference at P_coverage
3. If interference > threshold:
     Reduce power to meet interference constraint
4. Return optimized power with rationale
```

### Airtime Fairness (Gini Coefficient)

```
Gini = Σ|x_i - x_j| / (2n² * mean(x))

Where:
- x_i = airtime for client i
- n = number of clients
- 0 = perfect fairness
- 1 = maximum unfairness
```

## Testing

The library includes comprehensive tests covering:
- Channel planning algorithms (various scenarios)
- TX power optimization (coverage vs. interference)
- QoS/WMM analysis (all issue types)
- Client steering (band and AP)
- Mesh network analysis (backhaul quality)
- Enterprise feature validation

Run tests:
```bash
./gradlew :libs:network-optimizer:test
```

Generate test report:
```bash
./gradlew :libs:network-optimizer:test --tests "*"
# Report: build/reports/tests/test/index.html
```

## Performance Characteristics

### Computational Complexity

- **Channel Planning**: O(C × N) where C = channels, N = APs
- **TX Power Optimization**: O(N²) for interference calculation
- **QoS Analysis**: O(M) where M = clients
- **Client Steering**: O(N) per steering decision
- **Mesh Analysis**: O(L) where L = links

### Memory Footprint

- **AutoChannelPlanner**: O(C × N) - channel scores per AP
- **TxPowerOptimizer**: O(N²) - interference matrix
- **QosAnalyzer**: O(M) - per-client metrics
- **Mesh Analyzer**: O(L) - link quality map

## Dependencies

### Internal Modules
- `core-model`: Data models (ApCluster, WiFiBand, etc.)
- `core-rf`: RF metrics (RSSI, SNR, distance estimation, path loss)
- `topology`: Multi-AP analysis (ApClusterer, coverage analysis)

### External Libraries
- Kotlin stdlib
- Kotlin coroutines (for async operations)

## Standards Compliance

- **IEEE 802.11**: WiFi standards
- **IEEE 802.11k**: Radio resource measurement
- **IEEE 802.11r**: Fast BSS transition
- **IEEE 802.11v**: Wireless network management
- **IEEE 802.11e**: QoS (WMM)
- **IEEE 802.11s**: Mesh networking

## Version History

### 1.0.0 (2025-11-22)
- Initial release (Phase 7)
- Channel planning with DFS support
- TX power optimization
- QoS/WMM analysis
- Client steering (band and AP)
- Mesh network analysis
- Enterprise feature analysis
- 500+ comprehensive tests

## Future Enhancements

- **Machine Learning**: ML-based optimization
- **Predictive Analytics**: Traffic forecasting
- **Advanced Mesh**: Multi-hop optimization
- **Controller Integration**: API integration with enterprise controllers
- **Live Testing**: Active validation of recommendations

## License

Part of WiFi Intelligence Android application.
Copyright © 2025 LAM Consulting LLC
