package com.smock210.mypet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R

class HealthScaleFragment : Fragment() {

    private lateinit var scaleValueText: MaterialTextView
    private var selectedValue = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_health_scale, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scaleValueText = view.findViewById(R.id.tv_scale_value)

        val buttons = listOf(
            view.findViewById<MaterialButton>(R.id.btn_scale_0),
            view.findViewById<MaterialButton>(R.id.btn_scale_1),
            view.findViewById<MaterialButton>(R.id.btn_scale_2),
            view.findViewById<MaterialButton>(R.id.btn_scale_3),
            view.findViewById<MaterialButton>(R.id.btn_scale_4),
            view.findViewById<MaterialButton>(R.id.btn_scale_5),
            view.findViewById<MaterialButton>(R.id.btn_scale_6),
            view.findViewById<MaterialButton>(R.id.btn_scale_7),
            view.findViewById<MaterialButton>(R.id.btn_scale_8),
            view.findViewById<MaterialButton>(R.id.btn_scale_9),
            view.findViewById<MaterialButton>(R.id.btn_scale_10)
        )

        val descriptions = resources.getStringArray(R.array.scale_descriptions)

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val value = (index + 1).toFloat()
                updateScale(value, descriptions[index])
            }
        }

        // Добавим кнопку "Сохранить"
        view.findViewById<MaterialButton>(R.id.btn_save_scale).setOnClickListener {
            if (selectedValue > 0) {
                // Здесь можно сохранить состояние в БД
                // Для демо просто показываем сообщение
                scaleValueText.text = getString(R.string.scale_saved, scaleValueText.text)
            }
        }
    }

    private fun updateScale(value: Float, description: String) {
        selectedValue = value
        scaleValueText.text = "$value / 10\n$description"
    }
}