package com.aistudy.aistudy.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.QuestionAnswer
import com.aistudy.aistudy.database.QuestionAnswerDao

class HistoryFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var chipAll: TextView
    private lateinit var chipMath: TextView
    private lateinit var chipScience: TextView
    private lateinit var chipCode: TextView
    private lateinit var chipHistory: TextView
    private lateinit var chipStarred: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: HistoryAdapter
    private var qaDao: QuestionAnswerDao? = null

    private val allItems = mutableListOf<QuestionAnswer>()
    private var subjectFilter = "all"
    private var starredOnly = false
    private var query = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Bind các View trên layout
        etSearch = view.findViewById(R.id.et_search)
        chipAll = view.findViewById(R.id.chip_all)
        chipMath = view.findViewById(R.id.chip_math)
        chipScience = view.findViewById(R.id.chip_science)
        chipCode = view.findViewById(R.id.chip_code)
        chipHistory = view.findViewById(R.id.chip_history)
        chipStarred = view.findViewById(R.id.chip_starred)
        rvHistory = view.findViewById(R.id.rv_history)
        tvEmpty = view.findViewById(R.id.tv_empty)

        // 2. Khởi tạo Room DAO
        val db = AppDatabase.getInstance(requireContext())
        qaDao = db.questionAnswerDao()

        // 3. Khởi tạo Adapter và gán cho RecyclerView[cite: 1]
        adapter = HistoryAdapter(
            object : HistoryAdapter.Listener {
                override fun onItemClick(qa: QuestionAnswer) {
                    val intent = Intent(requireContext(), AnswerActivity::class.java)
                    intent.putExtra(AnswerActivity.EXTRA_QA_ID, qa.id)
                    startActivity(intent)
                }

                override fun onStarClick(qa: QuestionAnswer) {
                    val dao = qaDao ?: return
                    dao.toggleStar(qa.id)
                    loadHistory()
                }
            },
            ::subjectLabel,
            ::relativeTime
        )
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        // 4. Cài đặt sự kiện bấm các chip lọc
        setupChips()

        // 5. Lắng nghe ô tìm kiếm (Search)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadHistory() // Load lại dữ liệu mỗi khi quay lại tab[cite: 1]
    }

    private fun loadHistory() {
        val dao = qaDao ?: return
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // Sửa lại thành getLong vì userId trong DB là kiểu Long
        val userId = prefs.getLong("current_user_id", 1L)

        val list = dao.getAllByUserId(userId)
        allItems.clear()
        allItems.addAll(list)
        applyFilter()
    }

    private fun setupChips() {
        val chips = listOf(chipAll, chipMath, chipScience, chipCode, chipHistory, chipStarred)

        chipAll.setOnClickListener {
            subjectFilter = "all"
            starredOnly = false
            styleChip(chipAll, chips)
            applyFilter()
        }
        chipMath.setOnClickListener {
            subjectFilter = "math"
            starredOnly = false
            styleChip(chipMath, chips)
            applyFilter()
        }
        chipScience.setOnClickListener {
            subjectFilter = "science"
            starredOnly = false
            styleChip(chipScience, chips)
            applyFilter()
        }
        chipCode.setOnClickListener {
            subjectFilter = "code"
            starredOnly = false
            styleChip(chipCode, chips)
            applyFilter()
        }
        chipHistory.setOnClickListener {
            subjectFilter = "history"
            starredOnly = false
            styleChip(chipHistory, chips)
            applyFilter()
        }
        chipStarred.setOnClickListener {
            starredOnly = true
            subjectFilter = "all"
            styleChip(chipStarred, chips)
            applyFilter()
        }
    }

    private fun styleChip(active: TextView, allChips: List<TextView>) {
        for (chip in allChips) {
            if (chip == active) {
                chip.setBackgroundResource(R.drawable.bg_history_chip_active)
                chip.setTextColor(requireContext().getColor(R.color.auth_primary_foreground))
            } else {
                chip.setBackgroundResource(R.drawable.bg_history_chip)
                chip.setTextColor(requireContext().getColor(R.color.auth_foreground))
            }
        }
    }

    private fun applyFilter() {
        val filtered = mutableListOf<QuestionAnswer>()
        for (qa in allItems) {
            if (subjectFilter != "all" && subjectFilter != qa.subject) {
                continue
            }
            if (starredOnly && !qa.starred) {
                continue
            }
            if (query.isNotEmpty() && qa.question?.lowercase()?.contains(query.lowercase()) != true) {
                continue
            }
            filtered.add(qa)
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun subjectLabel(subject: String?): String {
        return when (subject?.lowercase()) {
            "math" -> getString(R.string.subject_math)
            "science" -> getString(R.string.subject_science)
            "code" -> getString(R.string.subject_code)
            "history" -> getString(R.string.subject_history)
            else -> subject ?: "General"
        }
    }

    private fun relativeTime(createdAt: Long): String {
        val diff = System.currentTimeMillis() - createdAt
        val hours = diff / 3_600_000L
        if (hours < 1) {
            return getString(R.string.history_just_now)
        }
        if (hours < 24) {
            return getString(R.string.history_hours_ago, hours.toInt())
        }
        return getString(R.string.history_days_ago, (hours / 24).toInt())
    }
}