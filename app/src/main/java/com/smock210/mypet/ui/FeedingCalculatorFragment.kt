package com.smock210.mypet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R
import com.smock210.mypet.utils.FeedingCalculator

class FeedingCalculatorFragment : Fragment() {

    private lateinit var weightInput: TextInputEditText
    private lateinit var ageInput: TextInputEditText
    private lateinit var resultTextView: MaterialTextView
    private lateinit var activityGroup: RadioGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feeding_calculator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        weightInput = view.findViewById(R.id.et_weight)
        ageInput = view.findViewById(R.id.et_age)
        resultTextView = view.findViewById(R.id.tv_result)
        activityGroup = view.findViewById(R.id.rg_activity)

        view.findViewById<MaterialButton>(R.id.btn_calculate).setOnClickListener {
            calculate()
        }

        view.findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener {
            reset()
        }
    }

    private fun calculate() {
        val weight = weightInput.text?.toString()?.toDoubleOrNull()
        val age = ageInput.text?.toString()?.toIntOrNull() ?: 5

        if (weight == null || weight <= 0) {
            resultTextView.text = getString(R.string.weight_invalid)
            return
        }

        val calculator = FeedingCalculator()
        val activityLevel = when (activityGroup.checkedRadioButtonId) {
            R.id.rb_low -> FeedingCalculator.ActivityLevel.LOW
            R.id.rb_high -> FeedingCalculator.ActivityLevel.HIGH
            else -> FeedingCalculator.ActivityLevel.MEDIUM
        }
        val adjustedPortion = calculator.calculateDailyPortionWithActivity(weight, activityLevel)
        val agePortion = calculator.calculateDailyPortion(weight, age)
        val calories = calculator.calculateDailyCalories(weight)

        val portionPerMeal = calculator.dividePortion(adjustedPortion, 2)

        resultTextView.text = getString(
            R.string.feeding_result,
            portionPerMeal.toString(),
            calculator.roundToTwoDecimals(adjustedPortion * 1000),
            calculator.roundToTwoDecimals(agePortion * 1000),
            calories.toString()
        )
    }

    private fun reset() {
        weightInput.text?.clear()
        ageInput.text?.clear()
        activityGroup.clearCheck()
        resultTextView.text = getString(R.string.feeding_hint)
    }

    private fun FeedingCalculator.roundToTwoDecimals(value: Double): String {
        return String.format("%.1f", value)
    }
}