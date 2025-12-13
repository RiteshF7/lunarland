package com.termux.app.taskexecutor.model

/**
 * Configuration data class for DroidRun CLI flags and options.
 * 
 * This class encapsulates all available DroidRun command-line parameters
 * to provide a centralized and type-safe way to configure the agent.
 * 
 * @property provider LLM provider name (e.g., "GoogleGenAI", "OpenAI", "Anthropic")
 * @property model Model name to use (e.g., "models/gemini-2.5-flash")
 * @property device Device serial or IP address (null for auto-detection)
 * @property steps Maximum number of execution steps
 * @property reasoning Enable reasoning/planning mode (ManagerAgent + ExecutorAgent)
 * @property vision Enable vision capabilities for all agents
 * @property tcp Use TCP instead of content provider
 * @property debug Enable verbose logging/debug mode
 * @property saveTrajectory Level of execution trajectory saving ("none", "step", or "action")
 * @property configPath Path to custom configuration file
 */
data class DroidRunConfig(
    val provider: String = "GoogleGenAI",
    val model: String = "models/gemini-2.5-flash",
    val device: String? = null, // null = auto-detect, "127.0.0.1:5558" for local
    val steps: Int = 150,
    val reasoning: Boolean = true,
    val vision: Boolean = false,
    val tcp: Boolean = false,
    val debug: Boolean = false,
    val saveTrajectory: String = "none", // "none", "step", or "action"
    val configPath: String? = null // null = use default config
) {
    /**
     * Builds the command-line flags string for droidrun run command.
     * Always includes essential flags (steps, reasoning) and optional flags when set.
     */
    fun buildFlagsString(): String {
        val flags = mutableListOf<String>()
        
        // Provider flag (only if not default)
        if (provider.isNotBlank() && provider != "GoogleGenAI") {
            flags.add("--provider")
            flags.add(provider)
        }
        
        // Model flag (only if not default)
        if (model.isNotBlank() && model != "models/gemini-2.5-flash") {
            flags.add("--model")
            flags.add(model)
        }
        
        // Device flag (only if explicitly set)
        if (device != null && device.isNotBlank()) {
            flags.add("--device")
            flags.add(device)
        }
        
        // Steps flag - always include (required)
        if (steps > 0) {
            flags.add("--steps")
            flags.add(steps.toString())
        }
        
        // Reasoning flag - always include (required)
        if (reasoning) {
            flags.add("--reasoning")
        } else {
            flags.add("--no-reasoning")
        }
        
        // Vision flag (only if enabled)
        if (vision) {
            flags.add("--vision")
        }
        
        // TCP flag (only if enabled)
        if (tcp) {
            flags.add("--tcp")
        }
        
        // Debug flag (only if enabled)
        if (debug) {
            flags.add("--debug")
        }
        
        // Save trajectory flag (only if not "none")
        if (saveTrajectory.isNotBlank() && saveTrajectory != "none") {
            flags.add("--save-trajectory")
            flags.add(saveTrajectory)
        }
        
        // Config path flag (only if set)
        if (configPath != null && configPath.isNotBlank()) {
            flags.add("--config")
            flags.add(configPath)
        }
        
        return flags.joinToString(" ")
    }
    
    /**
     * Creates a copy of this config with updated values.
     * Useful for creating modified configurations.
     */
    fun copy(
        provider: String = this.provider,
        model: String = this.model,
        device: String? = this.device,
        steps: Int = this.steps,
        reasoning: Boolean = this.reasoning,
        vision: Boolean = this.vision,
        tcp: Boolean = this.tcp,
        debug: Boolean = this.debug,
        saveTrajectory: String = this.saveTrajectory,
        configPath: String? = this.configPath
    ): DroidRunConfig {
        return DroidRunConfig(
            provider = provider,
            model = model,
            device = device,
            steps = steps,
            reasoning = reasoning,
            vision = vision,
            tcp = tcp,
            debug = debug,
            saveTrajectory = saveTrajectory,
            configPath = configPath
        )
    }
    
    companion object {
        /**
         * Default configuration with sensible defaults for most use cases.
         */
        val DEFAULT = DroidRunConfig()
        
        /**
         * High-performance configuration with reasoning enabled and more steps.
         */
        val HIGH_PERFORMANCE = DroidRunConfig(
            steps = 200,
            reasoning = true,
            vision = false,
            debug = false
        )
        
        /**
         * Direct mode configuration (no reasoning, faster execution).
         */
        val DIRECT_MODE = DroidRunConfig(
            steps = 100,
            reasoning = false,
            vision = false
        )
        
        /**
         * Debug configuration with verbose logging enabled.
         */
        val DEBUG = DroidRunConfig(
            steps = 150,
            reasoning = true,
            debug = true,
            saveTrajectory = "step"
        )
        
        /**
         * Vision-enabled configuration for tasks requiring visual understanding.
         */
        val VISION = DroidRunConfig(
            steps = 150,
            reasoning = true,
            vision = true
        )
    }
}

