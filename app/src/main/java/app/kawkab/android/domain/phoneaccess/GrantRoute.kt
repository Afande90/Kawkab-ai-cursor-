package app.kawkab.android.domain.phoneaccess

/**
 * What the Grant button does next (task 23j).
 *
 * After a denial, Android stops showing its dialog once the owner chose "Don't ask again":
 * the request returns at once, denied, and `shouldShowRequestPermissionRationale` is false.
 * Grant then opens App info → Permissions instead of doing nothing.
 */
enum class GrantRoute {
    /** The permission is on. Resume the action that needed it. */
    RESUME,

    /** Android will still show its dialog. Ask again. */
    ASK,

    /** Android will not show its dialog. Open App info → Permissions. */
    OPEN_APP_SETTINGS,
    ;

    /** The decision. */
    companion object {
        /**
         * @param granted Whether the permission is on now.
         * @param canShowDialog `shouldShowRequestPermissionRationale` for the permission.
         * @param deniedBefore Whether a request on this screen has already been denied.
         * @return The next step.
         */
        fun next(granted: Boolean, canShowDialog: Boolean, deniedBefore: Boolean): GrantRoute = when {
            granted -> RESUME
            deniedBefore && !canShowDialog -> OPEN_APP_SETTINGS
            else -> ASK
        }
    }
}
