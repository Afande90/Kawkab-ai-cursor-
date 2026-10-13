package app.knotwork.android.data.backup

/**
 * Which settings go into a backup and which stay with the phone.
 *
 * Every key of the app's DataStore has exactly one entry — `PreferenceBackupPolicyTest` holds the table
 * against the key census, so a new setting does not build until someone decides. A key not in the
 * table (one an older release left behind) is never exported.
 */
internal object PreferenceBackupPolicy {

    /** What a restore does with one setting. */
    enum class Membership {

        /** Written into the archive and restored in place of this phone's value. */
        TRAVELS,

        /** Never written into the archive: the phone keeps its own value. */
        STAYS,

        /** Never written into the archive, and cleared by a restore. */
        RESETS,
    }

    /**
     * A setting's value type, named as the settings entry writes it.
     *
     * @property wire The type's name in the entry.
     */
    enum class ValueType(val wire: String) {
        BOOLEAN("boolean"),
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        STRING("string"),
        STRING_SET("stringSet"),
        BYTE_ARRAY("byteArray"),
        ;

        /** The type by its name in the entry. */
        companion object {
            /**
             * @param wire A type name as the entry writes it.
             * @return The type, or `null` for a name this build does not know.
             */
            fun ofWire(wire: String): ValueType? = entries.firstOrNull { it.wire == wire }
        }
    }

    /**
     * Keys that describe this phone or this install rather than the user's choices, and why each stays.
     */
    private val STAYING: Map<String, String> = mapOf(
        "is_first_launch" to "first-run seeding already ran on this phone",
        "has_completed_onboarding" to "onboarding covers this phone's model download and permissions",
        "local_model_backend" to "CPU, GPU or NPU depends on this phone's hardware",
        "last_init_backend_attempt" to "this phone's native-crash sentinel",
        "local_backend_failure_streak" to "this phone's crash count for the backend",
        "last_test_probe_result" to "a benchmark of this phone",
        "crash_reporting_enabled" to "consent given to this build; the foss build has no collector",
        "share_admission_times" to "this phone's rate-limit ledger",
        "hugging_face_token" to "a legacy plain copy of a secret; secrets travel only through the secrets entry",
    )

    /** Keys a restore clears, and why. */
    private val RESETTING: Map<String, String> = mapOf(
        "external_automation_enabled" to "which apps may run a pipeline is decided again on the new phone",
    )

    /**
     * Keys that travel — the user's settings, and bindings to rows of the database that travels with them — each
     * with the type the app declares it with. A restore writes a value only when it has that type: a setting read
     * back as another type would fail every read of it.
     */
    private val TRAVELLING: Map<String, ValueType> = mapOf(
        "active_embedding_provider_id" to ValueType.STRING,
        "allowed_http_domains" to ValueType.STRING,
        "app_function_risk_overrides" to ValueType.STRING,
        "approved_cleartext_origins" to ValueType.STRING_SET,
        "audio_max_duration_sec" to ValueType.INT,
        "auto_extract_enabled" to ValueType.BOOLEAN,
        "background_approval_window_hours" to ValueType.INT,
        "block_destructive_tools" to ValueType.BOOLEAN,
        "block_network_from_local_model" to ValueType.BOOLEAN,
        "chat_history_compression_enabled" to ValueType.BOOLEAN,
        "chat_history_compression_threshold_tokens" to ValueType.INT,
        "chat_history_live_window_size" to ValueType.INT,
        "cloud_retry_base_delay_ms" to ValueType.LONG,
        "cloud_retry_max_attempts" to ValueType.INT,
        "console_preferred_tab" to ValueType.STRING,
        "current_chat_session_id" to ValueType.STRING,
        "default_pipeline_id" to ValueType.STRING,
        "disabled_app_functions" to ValueType.STRING_SET,
        "disabled_mcp_tools" to ValueType.STRING_SET,
        "external_automation_pipeline_id" to ValueType.STRING,
        "http_tool_max_response_bytes" to ValueType.LONG,
        "last_reembed_provider_id" to ValueType.STRING,
        "max_context_length" to ValueType.INT,
        "max_memory_chunks" to ValueType.INT,
        "mcp_server_urls" to ValueType.STRING_SET,
        "mcp_servers_json" to ValueType.STRING,
        "memory_compaction_age_days" to ValueType.INT,
        "memory_compaction_enabled" to ValueType.BOOLEAN,
        "memory_last_compacted_at" to ValueType.LONG,
        "memory_recency_half_life_days" to ValueType.INT,
        "memory_search_threshold" to ValueType.FLOAT,
        "memory_search_top_k" to ValueType.INT,
        "memory_summary_default_limit" to ValueType.INT,
        "pipeline_max_nesting_depth" to ValueType.INT,
        "pipeline_max_steps" to ValueType.INT,
        "pipeline_max_steps_background" to ValueType.INT,
        "quick_settings_tile_pipeline_id" to ValueType.STRING,
        "requires_user_confirmation" to ValueType.BOOLEAN,
        "resume_max_age_hours" to ValueType.INT,
        "run_max_tokens" to ValueType.INT,
        "run_max_tokens_background" to ValueType.INT,
        "scheduled_task_notifications" to ValueType.BOOLEAN,
        "share_reuse_session" to ValueType.BOOLEAN,
        "share_target_pipeline_id" to ValueType.STRING,
        "structured_output_max_repairs" to ValueType.INT,
        "system_prompt_prefix" to ValueType.STRING,
        "temperature" to ValueType.FLOAT,
        "tool_approval_policy" to ValueType.STRING,
        "tool_call_timeout_ms" to ValueType.LONG,
        "top_k" to ValueType.INT,
        "top_p" to ValueType.FLOAT,
        "trace_retention_max_age_days" to ValueType.INT,
        "trace_retention_runs_per_session" to ValueType.INT,
        "usage_telemetry_enabled" to ValueType.BOOLEAN,
        "verbose_memory_logging_enabled" to ValueType.BOOLEAN,
        "workspace_max_file_size_bytes" to ValueType.LONG,
        "workspace_max_total_bytes" to ValueType.LONG,
        "workspace_read_token_budget" to ValueType.INT,
    )

    /** Every key the table decides, by name. */
    val decided: Map<String, Membership> =
        TRAVELLING.keys.associateWith { Membership.TRAVELS } +
            STAYING.keys.associateWith { Membership.STAYS } +
            RESETTING.keys.associateWith { Membership.RESETS }

    /**
     * The type a travelling setting is declared with.
     *
     * @param key A DataStore key name.
     * @return Its type, or `null` for a key that does not travel.
     */
    fun typeOf(key: String): ValueType? = TRAVELLING[key]

    /**
     * What happens to [key] on backup and restore.
     *
     * @param key A DataStore key name.
     * @return Its membership; [Membership.STAYS] for a key the table does not know.
     */
    fun membershipOf(key: String): Membership = decided[key] ?: Membership.STAYS

    /**
     * Why [key] does not travel.
     *
     * @param key A key whose membership is [Membership.STAYS] or [Membership.RESETS].
     * @return The reason, or `null` for a travelling or unknown key.
     */
    fun reasonFor(key: String): String? = STAYING[key] ?: RESETTING[key]
}
