package com.smock210.mypet.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.Vaccination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaccinationAdapter(
    private val onItemClick: (Vaccination) -> Unit,
    private val onDoneChange: (Vaccination, Boolean) -> Unit,
    private val onDelete: (Vaccination) -> Unit
) : ListAdapter<Vaccination, VaccinationAdapter.Vh>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vaccination, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.date.text = dateFormat.format(Date(item.dateMillis))
        holder.notes.text = item.notes
        holder.notes.visibility = if (item.notes.isBlank()) View.GONE else View.VISIBLE
        holder.done.setOnCheckedChangeListener(null)
        holder.done.isChecked = item.done
        holder.done.setOnCheckedChangeListener { _, checked -> onDoneChange(item, checked) }
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_vaccine_name)
        val date: TextView = view.findViewById(R.id.tv_vaccine_date)
        val notes: TextView = view.findViewById(R.id.tv_vaccine_notes)
        val done: CheckBox = view.findViewById(R.id.cb_vaccine_done)
        val delete: ImageButton = view.findViewById(R.id.btn_delete_vaccine)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Vaccination>() {
            override fun areItemsTheSame(o: Vaccination, n: Vaccination) = o.id == n.id
            override fun areContentsTheSame(o: Vaccination, n: Vaccination) = o == n
        }
    }
}
