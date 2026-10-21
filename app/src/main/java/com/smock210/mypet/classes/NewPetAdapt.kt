package com.smock210.mypet.classes

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.PetData

class NewPetAdapt(
    private val listener: OnItemClickListener,
    private val listener2: OnLongClickListener
) : ListAdapter<PetData, NewPetAdapt.MyViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_row, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val pet = getItem(position)
        holder.largeTextView.text = pet.name ?: "—"
        holder.smallImageView.setImageResource(R.drawable.ic_launcher_foreground)
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, View.OnLongClickListener {
        val largeTextView: TextView = itemView.findViewById(R.id.label)
        val smallImageView: ImageView = itemView.findViewById(R.id.imageViewPet)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                v?.setBackgroundColor(Color.parseColor("#000000"))
                listener.onItemClick(position)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                v?.setBackgroundColor(Color.parseColor("#545454"))
                listener2.OnLongClickListener(position)
            }
            return true
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnLongClickListener {
        fun OnLongClickListener(position: Int)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PetData>() {
            override fun areItemsTheSame(oldItem: PetData, newItem: PetData): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PetData, newItem: PetData): Boolean =
                oldItem == newItem
        }
    }
}
