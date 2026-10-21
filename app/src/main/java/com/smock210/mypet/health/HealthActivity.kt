package com.smock210.mypet.health

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.smock210.mypet.R
import com.smock210.mypet.databinding.ActivityHealthBinding
import com.smock210.mypet.pdf.HealthReportExporter
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class HealthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthBinding
    private val viewModel: PetViewModel by viewModels()
    private var petId: Long = 0L
    private var petName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        petId = intent.getLongExtra("pet_id", 0L)
        petName = intent.getStringExtra("pet_name") ?: getString(R.string.pet_generic)
        title = getString(R.string.health_title_format, petName)

        val adapter = HealthTabsAdapter(supportFragmentManager)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.tabTitle(position)
        }.attach()

        viewModel.loadVaccinations(petId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.health_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_health_report) {
            generateReport()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Генерирует PDF-отчёт по прививкам и визитам (FIX_PLAN.md, 5.2). */
    private fun generateReport() {
        lifecycleScope.launch {
            // first() подписывается на Flow и дожидается данных текущего питомца
            val vaccinations = withTimeoutOrNull(DATA_WAIT_MS) { viewModel.vaccinations.first() }.orEmpty()
            val visits = withTimeoutOrNull(DATA_WAIT_MS) { viewModel.visits.first() }.orEmpty()
            val ok = withContext(Dispatchers.IO) {
                HealthReportExporter(applicationContext)
                    .generateHealthReport(petName, vaccinations, visits)
            }
            Toast.makeText(
                this@HealthActivity,
                if (ok) R.string.report_saved else R.string.report_error,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val DATA_WAIT_MS = 3000L
    }

    inner class HealthTabsAdapter(fm: FragmentManager) :
        FragmentStateAdapter(fm, this@HealthActivity.lifecycle) {
        private val tabs = listOf(
            getString(R.string.tab_vaccinations),
            getString(R.string.tab_doctor)
        )

        /** Заголовок вкладки (ViewPager2 + TabLayoutMediator). */
        fun tabTitle(position: Int): String = tabs[position]

        override fun getItemCount() = tabs.size

        override fun createFragment(position: Int): Fragment {
            val f = when (position) {
                0 -> VaccinationFragment()
                else -> VetVisitFragment()
            }
            f.arguments = Bundle().apply { putLong("pet_id", petId) }
            return f
        }
    }
}
