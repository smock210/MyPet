package com.smock210.mypet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.smock210.mypet.ui.ExportDataFragment
import com.smock210.mypet.ui.FeedingCalculatorFragment
import com.smock210.mypet.ui.HealthScaleFragment

/**
 * Универсальный контейнер для служебных экранов (калькулятор, экспорт и т.д.).
 * FIX_PLAN.md, 2.4.
 */
class ToolsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOOL = "tool"
        const val TOOL_FEEDING = "feeding"
        const val TOOL_EXPORT = "export"
        const val TOOL_HEALTH_SCALE = "health_scale"
        const val TOOL_MAPS = "maps"
        const val TOOL_VK_DIAGNOSTICS = "vk_diagnostics"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        if (savedInstanceState == null) {
            val fragment: Fragment = when (intent.getStringExtra(EXTRA_TOOL)) {
                TOOL_EXPORT -> ExportDataFragment()
                TOOL_HEALTH_SCALE -> HealthScaleFragment()
                TOOL_MAPS -> com.smock210.mypet.maps.MapsFragment()
                TOOL_VK_DIAGNOSTICS -> com.smock210.mypet.ui.VkDiagnosticsFragment()
                else -> FeedingCalculatorFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.tools_fragment_container, fragment)
                .commit()
        }
    }
}
