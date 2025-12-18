package io.lamco.netkit.exporttools.tools

/**
 * Template processing engine for dynamic content generation.
 *
 * Provides variable substitution, conditional rendering, and loop processing
 * for generating dynamic report content from templates.
 *
 * ## Features
 * - **Variable Substitution**: ${variable} syntax
 * - **Conditional Blocks**: {{if condition}}...{{endif}}
 * - **Loops**: {{for item in list}}...{{endfor}}
 * - **Nested Templates**: Include other templates
 * - **Filters**: Apply formatting functions
 * - **Escaping**: Context-aware escaping (HTML, JSON, etc.)
 *
 * ## Syntax
 *
 * ### Variables
 * ```
 * Hello, ${user.name}!
 * Signal: ${signal.rssi}dBm
 * ```
 *
 * ### Conditionals
 * ```
 * {{if security.score < 50}}
 * WARNING: Low security score!
 * {{endif}}
 * ```
 *
 * ### Loops
 * ```
 * {{for network in networks}}
 * - ${network.ssid}: ${network.securityType}
 * {{endfor}}
 * ```
 *
 * ### Filters
 * ```
 * ${date|formatDate}
 * ${number|round:2}
 * ${text|uppercase}
 * ```
 *
 * @since 1.0.0
 */
class TemplateEngine {
    private val variables = mutableMapOf<String, Any?>()
    private val filters = mutableMapOf<String, (Any?, List<String>) -> String>()

    init {
        registerDefaultFilters()
    }

    /**
     * Registers a variable for substitution.
     *
     * @param name Variable name
     * @param value Variable value
     */
    fun setVariable(
        name: String,
        value: Any?,
    ) {
        variables[name] = value
    }

    /**
     * Registers multiple variables.
     *
     * @param vars Map of variable names to values
     */
    fun setVariables(vars: Map<String, Any?>) {
        variables.putAll(vars)
    }

    /**
     * Registers a custom filter.
     *
     * @param name Filter name
     * @param filter Filter function taking value and arguments
     */
    fun registerFilter(
        name: String,
        filter: (Any?, List<String>) -> String,
    ) {
        filters[name] = filter
    }

    /**
     * Processes template with registered variables.
     *
     * @param template Template string
     * @return Processed string with substitutions
     */
    fun process(template: String): String {
        var result = template

        // Process loops FIRST so loop variables are set before variable substitution
        result = processLoops(result)

        // Process conditionals {{if}}...{{endif}}
        result = processConditionals(result)

        // Process variable substitutions ${var}
        result = processVariables(result)

        return result
    }

    /**
     * Processes variable substitutions.
     */
    private fun processVariables(template: String): String {
        val regex = """\$\{([^}]+)}""".toRegex()
        return regex.replace(template) { matchResult ->
            val expression = matchResult.groupValues[1]
            val (varPath, filterSpec) = parseExpression(expression)
            val value = resolveVariable(varPath)
            applyFilters(value, filterSpec)
        }
    }

    /**
     * Processes conditional blocks.
     */
    private fun processConditionals(template: String): String {
        var result = template
        val regex = """\{\{if\s+([^}]+)}}(.*?)\{\{endif}}""".toRegex(RegexOption.DOT_MATCHES_ALL)

        while (regex.containsMatchIn(result)) {
            result =
                regex.replace(result) { matchResult ->
                    val condition = matchResult.groupValues[1]
                    val content = matchResult.groupValues[2]

                    if (evaluateCondition(condition)) {
                        content
                    } else {
                        ""
                    }
                }
        }

        return result
    }

    /**
     * Processes loop blocks.
     */
    private fun processLoops(template: String): String {
        var result = template
        val regex = """\{\{for\s+(\w+)\s+in\s+([^}]+)}}(.*?)\{\{endfor}}""".toRegex(RegexOption.DOT_MATCHES_ALL)

        while (regex.containsMatchIn(result)) {
            result =
                regex.replace(result) { matchResult ->
                    val itemVar = matchResult.groupValues[1]
                    val listPath = matchResult.groupValues[2]
                    val content = matchResult.groupValues[3]

                    val list = resolveVariable(listPath) as? List<*> ?: emptyList<Any>()
                    list.joinToString("") { item ->
                        val engine = TemplateEngine()
                        engine.setVariables(variables)
                        engine.setVariable(itemVar, item)
                        engine.process(content)
                    }
                }
        }

        return result
    }

    /**
     * Parses expression into variable path and filter spec.
     */
    private fun parseExpression(expression: String): Pair<String, List<Pair<String, List<String>>>> {
        val parts = expression.split("|")
        val varPath = parts[0].trim()
        // Don't trim filter specs - preserves spaces in arguments like "join: - "
        val filterSpecs = parts.drop(1).map { parseFilter(it) }
        return varPath to filterSpecs
    }

    /**
     * Parses filter specification.
     * Preserves spaces in filter arguments (important for join separators like " - ").
     */
    private fun parseFilter(spec: String): Pair<String, List<String>> {
        val colonIndex = spec.indexOf(':')
        if (colonIndex == -1) {
            return spec.trim() to emptyList()
        }
        val filterName = spec.substring(0, colonIndex).trim()
        // Preserve the argument as-is (including spaces) - important for separators
        val argString = spec.substring(colonIndex + 1)
        return filterName to listOf(argString)
    }

    /**
     * Resolves variable by path (supports nested access like "user.name").
     */
    private fun resolveVariable(path: String): Any? {
        val parts = path.split(".")
        var current: Any? = variables[parts[0]]

        for (part in parts.drop(1)) {
            current =
                when (current) {
                    is Map<*, *> -> current[part]
                    else -> null
                }
        }

        return current
    }

    /**
     * Applies filters to value.
     */
    private fun applyFilters(
        value: Any?,
        filterSpecs: List<Pair<String, List<String>>>,
    ): String {
        if (filterSpecs.isEmpty()) {
            return value?.toString() ?: ""
        }

        // Keep as Any? through filter chain so collection filters work
        var current: Any? = value
        for ((filterName, args) in filterSpecs) {
            val filter = filters[filterName] ?: continue
            current = filter(current, args)
        }

        return current?.toString() ?: ""
    }

    /**
     * Evaluates conditional expression (simplified).
     */
    private fun evaluateCondition(condition: String): Boolean {
        // Simple comparison operators: <, >, ==, !=
        val comparisonRegex = """(\S+)\s*([<>=!]+)\s*(\S+)""".toRegex()
        val match = comparisonRegex.find(condition)

        return if (match != null) {
            val left = resolveVariable(match.groupValues[1])
            val operator = match.groupValues[2]
            val right = match.groupValues[3]

            compareValues(left, operator, right)
        } else {
            // Simple boolean variable
            val value = resolveVariable(condition.trim())
            value == true || value == "true"
        }
    }

    /**
     * Compares values with operator.
     */
    private fun compareValues(
        left: Any?,
        operator: String,
        right: String,
    ): Boolean {
        val leftNum = left?.toString()?.toDoubleOrNull()
        val rightNum = right.toDoubleOrNull()

        return when (operator) {
            "==" -> left?.toString() == right
            "!=" -> left?.toString() != right
            "<" -> leftNum != null && rightNum != null && leftNum < rightNum
            ">" -> leftNum != null && rightNum != null && leftNum > rightNum
            "<=" -> leftNum != null && rightNum != null && leftNum <= rightNum
            ">=" -> leftNum != null && rightNum != null && leftNum >= rightNum
            else -> false
        }
    }

    /**
     * Registers default filters.
     */
    private fun registerDefaultFilters() {
        // Uppercase filter
        registerFilter("uppercase") { value, _ ->
            value?.toString()?.uppercase() ?: ""
        }

        // Lowercase filter
        registerFilter("lowercase") { value, _ ->
            value?.toString()?.lowercase() ?: ""
        }

        // Capitalize filter
        registerFilter("capitalize") { value, _ ->
            value?.toString()?.replaceFirstChar { it.uppercase() } ?: ""
        }

        // Round filter
        registerFilter("round") { value, args ->
            val num = value?.toString()?.toDoubleOrNull()
            val decimals = args.firstOrNull()?.toIntOrNull() ?: 0
            if (num != null) {
                "%.${decimals}f".format(num)
            } else {
                value?.toString() ?: ""
            }
        }

        // Default value filter
        registerFilter("default") { value, args ->
            if (value == null || value.toString().isBlank()) {
                args.firstOrNull() ?: ""
            } else {
                value.toString()
            }
        }

        // Length filter
        registerFilter("length") { value, _ ->
            when (value) {
                is Collection<*> -> value.size.toString()
                is Map<*, *> -> value.size.toString()
                is String -> value.length.toString()
                else -> "0"
            }
        }

        // First filter
        registerFilter("first") { value, _ ->
            when (value) {
                is List<*> -> value.firstOrNull()?.toString() ?: ""
                is String -> value.firstOrNull()?.toString() ?: ""
                else -> value?.toString() ?: ""
            }
        }

        // Last filter
        registerFilter("last") { value, _ ->
            when (value) {
                is List<*> -> value.lastOrNull()?.toString() ?: ""
                is String -> value.lastOrNull()?.toString() ?: ""
                else -> value?.toString() ?: ""
            }
        }

        // Join filter
        registerFilter("join") { value, args ->
            val separator = args.firstOrNull() ?: ", "
            when (value) {
                is Collection<*> -> value.joinToString(separator)
                else -> value?.toString() ?: ""
            }
        }

        // Escape HTML filter
        registerFilter("escapeHtml") { value, _ ->
            value
                ?.toString()
                ?.replace("&", "&amp;")
                ?.replace("<", "&lt;")
                ?.replace(">", "&gt;")
                ?.replace("\"", "&quot;")
                ?: ""
        }

        // Escape JSON filter
        registerFilter("escapeJson") { value, _ ->
            value
                ?.toString()
                ?.replace("\\", "\\\\")
                ?.replace("\"", "\\\"")
                ?.replace("\n", "\\n")
                ?.replace("\r", "\\r")
                ?.replace("\t", "\\t")
                ?: ""
        }
    }
}
