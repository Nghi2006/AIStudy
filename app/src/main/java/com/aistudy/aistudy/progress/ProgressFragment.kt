package com.aistudy.aistudy.progress

import com.aistudy.aistudy.auth.LoginActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDao
import com.aistudy.aistudy.database.QuestionAnswerDao
import kotlin.math.roundToInt

class ProgressFragment : Fragment() {

    private lateinit var tvUserEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var tvXpTotal: TextView
    private lateinit var tvLevel: TextView
    private lateinit var progressXp: ProgressBar
    private lateinit var tvXpPercent: TextView

    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatQuestions: TextView
    private lateinit var tvStatStars: TextView

    private lateinit var tvSubjectsEmpty: TextView
    private lateinit var rowMath: LinearLayout
    private lateinit var tvCountMath: TextView
    private lateinit var barMath: ProgressBar

    private lateinit var rowScience: LinearLayout
    private lateinit var tvCountScience: TextView
    private lateinit var barScience: ProgressBar

    private lateinit var rowCode: LinearLayout
    private lateinit var tvCountCode: TextView
    private lateinit var barCode: ProgressBar

    private lateinit var rowHistory: LinearLayout
    private lateinit var tvCountHistory: TextView
    private lateinit var barHistory: ProgressBar

    private lateinit var rowLanguage: LinearLayout
    private lateinit var tvCountLanguage: TextView
    private lateinit var barLanguage: ProgressBar

    private lateinit var badgeStarter: LinearLayout
    private lateinit var badgeWeek: LinearLayout
    private lateinit var badgeMath: LinearLayout
    private lateinit var badgeScholar: LinearLayout

    private var profileDao: ProfileDao? = null
    private var qaDao: QuestionAnswerDao? = null

    companion object {
        private const val XP_MAX = 1200
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUserEmail = view.findViewById(R.id.tv_user_email)
        btnLogout = view.findViewById(R.id.btn_logout)

        // 1. Bind các View trên layout
        tvXpTotal = view.findViewById(R.id.tv_xp_total)
        tvLevel = view.findViewById(R.id.tv_level)
        progressXp = view.findViewById(R.id.progress_xp)
        tvXpPercent = view.findViewById(R.id.tv_xp_percent)

        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatQuestions = view.findViewById(R.id.tv_stat_questions)
        tvStatStars = view.findViewById(R.id.tv_stat_stars)

        tvSubjectsEmpty = view.findViewById(R.id.tv_subjects_empty)

        rowMath = view.findViewById(R.id.row_math)
        tvCountMath = view.findViewById(R.id.tv_count_math)
        barMath = view.findViewById(R.id.bar_math)

        rowScience = view.findViewById(R.id.row_science)
        tvCountScience = view.findViewById(R.id.tv_count_science)
        barScience = view.findViewById(R.id.bar_science)

        rowCode = view.findViewById(R.id.row_code)
        tvCountCode = view.findViewById(R.id.tv_count_code)
        barCode = view.findViewById(R.id.bar_code)

        rowHistory = view.findViewById(R.id.row_history)
        tvCountHistory = view.findViewById(R.id.tv_count_history)
        barHistory = view.findViewById(R.id.bar_history)

        rowLanguage = view.findViewById(R.id.row_language)
        tvCountLanguage = view.findViewById(R.id.tv_count_language)
        barLanguage = view.findViewById(R.id.bar_language)

        badgeStarter = view.findViewById(R.id.badge_starter)
        badgeWeek = view.findViewById(R.id.badge_week)
        badgeMath = view.findViewById(R.id.badge_math)
        badgeScholar = view.findViewById(R.id.badge_scholar)

        // 2. Khởi tạo Database DAO
        val db = AppDatabase.getInstance(requireContext())
        profileDao = db.profileDao()
        qaDao = db.questionAnswerDao()

        btnLogout.setOnClickListener {
            logout()
        }
    }

    override fun onResume() {
        super.onResume()
        loadProgress() // Load lại dữ liệu mỗi khi người dùng mở tab Progress[cite: 1]
    }

    private fun logout() {
        // Xóa session đăng nhập
        requireContext()
            .getSharedPreferences(
                LoginActivity.PREFS_AUTH,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(LoginActivity.KEY_USER_ID)
            .apply()

        // Quay về màn hình Login
        val intent = Intent(requireContext(), LoginActivity::class.java)

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
    }

    private fun loadProgress() {
        val pDao = profileDao ?: return
        val qDao = qaDao ?: return

        val prefs = requireContext().getSharedPreferences(
            LoginActivity.PREFS_AUTH,
            Context.MODE_PRIVATE
        )

        val userId = prefs.getLong(LoginActivity.KEY_USER_ID, -1L)

        if (userId == -1L) return

        val db = AppDatabase.getInstance(requireContext())

        val user = db.userDao().getById(userId)
        tvUserEmail.text = user?.email ?: ""

        // Lấy thông tin Profile (XP, Streak, Level)
        val profile = pDao.getByUserId(userId)
        val xp = profile?.xp ?: 0
        val streak = profile?.streak ?: 0
        val level = profile?.level ?: 1

        // Bind XP card
        tvXpTotal.text = getString(R.string.progress_xp_percent, xp).replace("%", " XP")
        tvLevel.text = "Level ${profile?.level ?: 1} • Scholar"

        val xpPercent = minOf(100, ((xp * 100f) / XP_MAX).roundToInt())
        progressXp.progress = xpPercent
        tvXpPercent.text = getString(R.string.progress_xp_percent, xpPercent)

        // Lấy danh sách câu hỏi để thống kê
        val allQuestions = qDao.getAllByUserId(userId)
        val totalQuestions = allQuestions.size
        val starredCount = allQuestions.count { it.starred }

        // Bind 3 ô thống kê
        tvStatStreak.text = getString(R.string.progress_streak_value, streak)
        tvStatQuestions.text = totalQuestions.toString()
        tvStatStars.text = starredCount.toString()

        // Thống kê theo môn học
        val countMath = qDao.countBySubject(userId, "math")
        val countScience = qDao.countBySubject(userId, "science")
        val countCode = qDao.countBySubject(userId, "code")
        val countHistory = qDao.countBySubject(userId, "history")
        val countLanguage = qDao.countBySubject(userId, "language")

        val subjectTotal = countMath + countScience + countCode + countHistory + countLanguage
        val barDenom = if (subjectTotal > 0) subjectTotal else 1

        bindSubjectRow(rowMath, tvCountMath, barMath, countMath, barDenom)
        bindSubjectRow(rowScience, tvCountScience, barScience, countScience, barDenom)
        bindSubjectRow(rowCode, tvCountCode, barCode, countCode, barDenom)
        bindSubjectRow(rowHistory, tvCountHistory, barHistory, countHistory, barDenom)
        bindSubjectRow(rowLanguage, tvCountLanguage, barLanguage, countLanguage, barDenom)

        tvSubjectsEmpty.visibility = if (subjectTotal == 0) View.VISIBLE else View.GONE

        // Kiểm tra điều kiện mở khóa Badges
        setBadge(badgeStarter, totalQuestions >= 1)
        setBadge(badgeWeek, streak >= 7)
        setBadge(badgeMath, countMath >= 1)
        setBadge(badgeScholar, xp >= 1000)
    }

    private fun bindSubjectRow(
        row: LinearLayout,
        tvCount: TextView,
        bar: ProgressBar,
        count: Int,
        denom: Int
    ) {
        if (count <= 0) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        tvCount.text = count.toString()
        bar.progress = ((count * 100f) / denom).roundToInt()
    }

    private fun setBadge(badge: LinearLayout, earned: Boolean) {
        badge.setBackgroundResource(if (earned) R.drawable.bg_badge_earned else R.drawable.bg_badge_locked)
        badge.alpha = if (earned) 1f else 0.5f
    }
}