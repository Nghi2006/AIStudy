package com.aistudy.aistudy.ask

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private val SUBJECTS = arrayOf("math", "science", "code", "history", "language", "other")
        private const val XP_PER_QUESTION = 50

        private fun maskKey(key: String?): String {
            if (key == null || key.length < 8) return "(invalid)"
            return key.substring(0, 4) + "..." + key.substring(key.length - 4)
        }

        private fun truncate(s: String?, max: Int): String {
            if (s == null) return "null"
            if (s.length <= max) return s
            return s.substring(0, max) + "..."
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private var executor: ExecutorService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        progressLoading = view.findViewById(R.id.progress_loading)
        executor = Executors.newSingleThreadExecutor()

        btnSend.setOnClickListener { submitQuestion() }

        view.findViewById<View>(R.id.btn_suggestion_1).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_1))
        }
        view.findViewById<View>(R.id.btn_suggestion_2).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_2))
        }
        view.findViewById<View>(R.id.btn_suggestion_3).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_3))
        }
        view.findViewById<View>(R.id.btn_suggestion_4).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_4))
        }
    }

    private fun submitQuestion() {
        val question = editQuestion.text.toString().trim()
        if (TextUtils.isEmpty(question)) {
            Toast.makeText(requireContext(), R.string.ask_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(BuildConfig.GEMINI_API_KEY) ||
            "YOUR_KEY_HERE" == BuildConfig.GEMINI_API_KEY
        ) {
            Log.e(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        Log.d(
            TAG, "submit start questionLength=${question.length}" +
                    " apiKey=${maskKey(BuildConfig.GEMINI_API_KEY)}"
        )
        setLoading(true)

        val prefs = requireContext().getSharedPreferences(LoginActivity.PREFS_AUTH, 0)
        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1)

        val db = AppDatabase.getInstance(requireContext())
        val profileDao = db.profileDao()
        val qaDao = db.questionAnswerDao()
        val profile = profileDao.getByUserId(userId)

        val level = if (profile != null) profile.level else "high"
        val style = if (profile != null) profile.style else "stepwise"
        val prompt = buildPrompt(question, level, style)
        Log.d(TAG, "profile level=$level style=$style userId=$userId")

        executor?.execute {
            try {
                Log.d(TAG, "calling Gemini...")
                val raw = GeminiClient.ask(BuildConfig.GEMINI_API_KEY, prompt)
                if (raw.isEmpty()) {
                    Log.e(TAG, "Gemini returned empty text")
                    showError(getString(R.string.ask_error))
                    return@execute
                }
                Log.d(TAG, "raw response preview=${truncate(raw, 300)}")

                val jsonText = extractJson(raw)
                Log.d(TAG, "extracted json preview=${truncate(jsonText, 300)}")
                val json = JSONObject(jsonText)

                var subject = json.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = json.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = json.getJSONArray("steps")

                val finalAnswer = json.getString("finalAnswer")
                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                            " steps=${stepsArray.length()}" +
                            " finalAnswerLength=${finalAnswer.length}"
                )


                val qa = QuestionAnswer()
                qa.userId = userId
                qa.subject = subject
                qa.question = question
                qa.steps = stepsArray.toString()
                qa.finalAnswer = finalAnswer
                qa.starred = false
                qa.createdAt = System.currentTimeMillis()
                qa.difficulty = difficulty
                qa.extrasJson = null
                qa.reviewedAt = 0


                val qaId = qaDao.insert(qa)
                Log.d(TAG, "saved qaId=$qaId")

                if (profile != null) {
                    profile.xp += XP_PER_QUESTION
                    profileDao.update(profile)
                    Log.d(TAG, "xp updated to ${profile.xp}")
                }

                requireActivity().runOnUiThread {
                    Log.d(TAG, "opening AnswerActivity qaId=$qaId")
                    setLoading(false)
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                showError(getString(R.string.ask_error))
            }
        }
    }

    private fun showError(message: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            setLoading(false)
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnSend.setText(if (loading) R.string.ask_sending else R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun buildPrompt(question: String, level: String?, style: String?): String {
        val levelText = when (level) {
            "junior" -> "middle school student (grades 6-9)"
            "uni" -> "university student"
            else -> "high school student (grades 10-12)"
        }

        val styleText = when (style) {
            "short" -> "Answer very briefly — go straight to the answer."
            "detailed" -> "Explain in detail with examples."
            else -> "Explain step by step, one idea per step."
        }

        return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
                styleText + "\n" +
                "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
                "Classify the subject correctly (math/science/code/history/language/other).\n" +
                "Also rate difficulty as easy, medium, or hard for this student.\n\n" +
                "Return ONLY valid JSON (no markdown) with this structure:\n" +
                "{\n" +
                "  \"subject\": \"math|science|code|history|language|other\",\n" +
                "  \"difficulty\": \"easy|medium|hard\",\n" +
                "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
                "  \"finalAnswer\": \"string\"\n" +
                "}\n\n" +
                "Question: " + question

    }

    private fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("```")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1)
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return text
    }

    private fun isValidSubject(subject: String): Boolean {
        return SUBJECTS.contains(subject)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
    }
    private fun isValidDifficulty(difficulty: String): Boolean {
        return difficulty == "easy" ||
                difficulty == "medium" ||
                difficulty == "hard"
    }
}
