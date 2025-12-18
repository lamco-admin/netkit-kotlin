# NetKit Improvement Session Plan

**Date:** 2024-12-18
**Location:** `/home/greg/lamco-admin/staging/lamco-kotlin-libraries/netkit-extraction/`
**Status:** In Progress

---

## Objectives

### 1. Fix Flaky Test ✅
**Target:** `ExportQueueTest` - "cancel cancels pending job()"
- Identify timing/synchronization issue
- Add proper synchronization primitives
- Verify test passes consistently (10+ runs)

### 2. Apply Code Humanization Framework ✅
Apply lessons learned from IronRDP PR #1057 humanization:

**Narrative Analysis (Primary):**
- Read code for "what" comments vs "why" comments
- Identify excessive logging (3+ debug statements per function)
- Check for generic variable names (data, result, temp)
- Verify narrative flow (Setup → Guards → Action → Resolution)
- Look for uniform structure (AI tell)

**Pattern Detection (Secondary):**
```bash
# Critical tells
rg -l "utils|helpers|common" .
rg "fn (process|handle|do_|run)\s*\(" .

# Moderate tells
rg "^\s{16,}" --type kotlin
rg "(Manager|Handler|Processor)\b"
```

**Expected Changes:**
- Remove "what" comments that restate code
- Add sparse "why" comments for non-obvious decisions
- Reduce excessive logging
- Rename generic variables to domain-specific names
- Improve narrative flow where needed

### 3. Documentation Generation ✅
```bash
./gradlew dokkaHtmlAll
```
- Generate API documentation for all modules
- Review generated docs for quality
- Ensure all public APIs are documented

### 4. Quality Verification ✅
**Build & Test:**
```bash
./gradlew clean build
./gradlew test
```

**Expected Results:**
- All 276 tests passing (100%)
- Zero compiler warnings
- Clean build

---

## Success Criteria

Before marking session complete:

- [ ] Flaky test fixed and verified stable
- [ ] Humanization framework applied to all modules
- [ ] Code tells a clear narrative story
- [ ] Comments explain "why", not "what"
- [ ] Logging is strategic, not excessive
- [ ] All tests pass (100%)
- [ ] Dokka documentation generated
- [ ] Changes committed to lamco-admin

---

## Module Priority for Humanization

**High Priority (Core + Most Used):**
1. core (foundation - most important)
2. security (complex logic)
3. intelligence (complex logic)

**Medium Priority:**
4. diagnostics
5. wifi-optimizer
6. analytics

**Lower Priority:**
7. export (simpler code)

Focus on high-priority modules first for maximum impact.

---

## Humanization Process (Per Module)

### Step 1: Narrative Analysis (15-30 min)
1. Read through main source files
2. Note "what" comments (mark for removal)
3. Note excessive logging (mark for reduction)
4. Note generic variable names (mark for rename)
5. Note missing "why" explanations (mark for addition)

### Step 2: Pattern Detection (5 min)
```bash
cd {module}/src/main/kotlin
rg "// (Get|Set|Create|Update|Delete|Process|Handle)" .
rg "log\.|logger\." . -c | sort -t: -k2 -rn
```

### Step 3: Apply Transformations (30-60 min)
1. Remove "what" comments
2. Add strategic "why" comments (max 2-3 per file)
3. Reduce logging (≤1 per function)
4. Rename generic variables
5. Tighten whitespace

### Step 4: Verify (5 min)
```bash
./gradlew :{module}:test
```

---

## Notes

### Key Insights from IronRDP Session

1. **Pattern matching alone is insufficient**
   - Must read code for narrative issues
   - "What" comments, over-logging, uniform structure are invisible to grep

2. **Don't be dogmatic about "what" comments**
   - Reserve for genuinely complex parts that need clarification
   - Remove ones that just restate code

3. **Strategic logging > Excessive logging**
   - AI logs everything equally
   - Humans log at decision points and critical paths

4. **Kotlin idioms matter**
   - Use idiomatic patterns (let, apply, run when appropriate)
   - Avoid verbose patterns

---

## Current State

**Build:** ✅ Passing (verified 2024-12-18)
**Tests:** 275/276 passing (99.6%)
**Flaky Test:** ExportQueueTest async timing issue

**Ready to begin improvements.**

---

**Next:** Identify and fix flaky test
