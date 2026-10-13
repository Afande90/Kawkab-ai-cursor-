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

    /** Keys that travel: the user's settings, and bindings to rows of the database that travels with them. */
    private val TRAVELLING: Set<String> = setOf(
        "active_embedding_provider_id",
        "allowed_http_domains",
        "app_function_risk_overrides",
        "approved_cleartext_origins",
        "audio_max_duration_sec",
        "auto_extract_enabled",
        "background_approval_window_hours",
        "block_destructive_tools",
        "block_network_from_local_model",
        "chat_history_compression_enabled",
        "chat_history_compression_threshold_tokens",
        "chat_history_live_window_size",
        "cloud_retry_base_delay_ms",
        "cloud_retry_max_attempts",
        "console_preferred_tab",
        "current_chat_session_id",
        "default_pipeline_id",
        "disabled_app_functions",
        "disabled_mcp_tools",
        "external_automation_pipeline_id",
        "http_tool_max_response_bytes",
        "last_reembed_provider_id",
        "max_context_length",
        "max_memory_chunks",
        "mcp_server_urls",
        "mcp_servers_json",
        "memory_compaction_age_days",
        "memory_compaction_enabled",
        "memory_last_compacted_at",
        "memory_recency_half_life_days",
        "memory_search_threshold",
        "memory_search_top_k",
        "memory_summary_default_limit",
        "pipeline_max_nesting_depth",
        "pipeline_max_steps",
        "pipeline_max_steps_background",
        "quick_settings_tile_pipeline_id",
        "requires_user_confirmation",
        "resume_max_age_hours",
        "run_max_tokens",
        "run_max_tokens_background",
        "scheduled_task_notifications",
        "share_reuse_session",
        "share_target_pipeline_id",
        "structured_output_max_repairs",
        "system_prompt_prefix",
        "temperature",
        "tool_approval_policy",
        "tool_call_timeout_ms",
        "top_k",
        "top_p",
        "trace_retention_max_age_days",
        "trace_retention_runs_per_session",
        "usage_telemetry_enabled",
        "verbose_memory_logging_enabled",
        "workspace_max_file_size_bytes",
        "workspace_max_total_bytes",
        "workspace_read_token_budget",
    )

    /** Every key the table decides, by name. */
    val decided: Map<String, Membership> =
        TRAVELLING.associateWith { Membership.TRAVELS } +
            STAYING.keys.associateWith { Membership.STAYS } +
            RESETTING.keys.associateWith { Membership.RESETS }

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
