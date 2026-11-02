package app.kawkab.android.data.decision

import android.content.Context
import app.kawkab.android.domain.decision.DecisionAudit
import app.kawkab.android.domain.decision.FileDecisionAudit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Decision inputs, confidence and actual outcomes remain in private no-backup storage. */
@Singleton
class AndroidDecisionAudit @Inject constructor(@ApplicationContext context: Context) :
    DecisionAudit by FileDecisionAudit(File(context.noBackupFilesDir, "decision/decisions.jsonl"))
