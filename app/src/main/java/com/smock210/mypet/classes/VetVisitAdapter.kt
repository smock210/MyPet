package com.smock210.mypet.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.VetVisit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VetVisitAdapter(
    private val onItemClick: (VetVisit) -> Unit,
    private val onDelete: (VetVisit) -> Unit
) : ListAdapter<VetVisit, VetVisitAdapter.Vh>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vet_visit, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = getItem(position)
        holder.clinic.text = item.clinicName
        holder.doctor.text = item.doctorName
        holder.doctor.visibility = if (item.doctorName.isBlank()) View.GONE else View.VISIBLE
        holder.date.text = dateFormat.format(Date(item.dateMillis))
        holder.diagnosis.text = item.diagnosis
        holder.diagnosis.visibility = if (item.diagnosis.isBlank()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val clinic: TextView = view.findViewById(R.id.tv_visit_clinic)
        val doctor: TextView = view.findViewById(R.id.tv_visit_doctor)
        val date: TextView = view.findViewById(R.id.tv_visit_date)
        val diagnosis: TextView = view.findViewById(R.id.tv_visit_diagnosis)
        val delete: ImageButton = view.findViewById(R.id.btn_delete_visit)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VetVisit>() {
            override fun areItemsTheSame(o: VetVisit, n: VetVisit) = o.id == n.id
            override fun areContentsTheSame(o: VetVisit, n: VetVisit) = o == n
        }
    }
}
