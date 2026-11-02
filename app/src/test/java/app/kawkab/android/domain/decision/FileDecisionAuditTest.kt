package app.kawkab.android.domain.decision

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Decisions and actual gate outcomes are joined locally, including denials and follow-ups. */
class FileDecisionAuditTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun decisionAndDeniedOutcomeSurviveOnDiskWithTheSameIdentity() {
        val file = File(temporary.root, "decision/log.jsonl")
        val audit = FileDecisionAudit(file) { 1000L }
        assertTrue(audit.decision(record("first")))
        audit.outcome("first", "call_phone", "Execution denied by user")
        val lines = file.readLines().map(::JSONObject)
        assertEquals(0.85, lines[0].getDouble("threshold"), 0.0)
        assertEquals("selected", lines[0].getString("outcome"))
        assertEquals(lines[0].getString("id"), lines[1].getJSONArray("decision_ids").getString(0))
        assertEquals("Execution denied by user", lines[1].getString("observation"))
    }

    @Test fun aLaterTurnRecordsTheAnswerAndDoesNotMisattributeItsTool() {
        val file = File(temporary.root, "log.jsonl")
        val audit = FileDecisionAudit(file)
        audit.decision(record("first").copy(result = DecisionResult.Ask("Which one?")))
        audit.begin("first", "the second")
        audit.outcome("first", "open_app", "Opened")
        val lines = file.readLines().map(::JSONObject)
        assertEquals("owner_follow_up", lines[1].getString("status"))
        assertEquals("the second", lines[1].getJSONObject("details").getString("answer"))
        assertEquals(0, lines[2].getJSONArray("decision_ids").length())
    }

    @Test fun independentSessionsCannotBorrowEachOthersDecisionIds() {
        val file = File(temporary.root, "log.jsonl")
        val audit = FileDecisionAudit(file)
        audit.decision(record("first"))
        audit.decision(record("second"))
        audit.outcome("second", "set_alarm", "Alarm set")
        val lines = file.readLines().map(::JSONObject)
        assertEquals(lines[1].getString("id"), lines[2].getJSONArray("decision_ids").getString(0))
        assertEquals(1, lines[2].getJSONArray("decision_ids").length())
    }

    @Test fun anUnwritableLogCannotAuthorizeAutomaticAction() {
        val parent = temporary.newFile("not-a-directory")
        assertFalse(FileDecisionAudit(File(parent, "log.jsonl")).decision(record("first")))
    }

    private fun record(session: String): DecisionRecord = DecisionRecord(
        session, "call Abdalla", "Which contact?", listOf("first", "second"), doubleArrayOf(5.0, 0.0),
        DecisionResult.Chosen("first", 0.99), "model", 20L,
    )
}
