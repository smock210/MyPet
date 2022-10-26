package com.smock210.mypet.clasess

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R


class NewPetAdapt {
    class NewPetadapt (private val names: List<String>, private val listener: OnItemClickListener,private val listener2: OnLongClickListener) : RecyclerView.Adapter<NewPetadapt.MyViewHolder>() {
        //var sList = arrayOf("One1", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen")

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            val itemView =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_row, parent, false)
            return MyViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.largeTextView.text = names[position]
        }


        override fun getItemCount(): Int {
            return names.size
        }
        inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {

            val largeTextView: TextView = itemView.findViewById(R.id.label)
            var smallTextView: ImageView = itemView.findViewById(R.id.imageViewPet)

            init {
                smallTextView.setImageResource(R.drawable.ic_launcher_foreground)
                itemView.setOnClickListener(this)
                itemView.setOnLongClickListener(this);
            }

            override fun onClick(v: View?) {
                val position = adapterPosition
                print("click")
                if (position != RecyclerView.NO_POSITION) {
                    v?.setBackgroundColor(Color.parseColor("#000000"))
                    listener.onItemClick(position)
                }
            }

            override fun onLongClick(v: View?): Boolean {
                val position = adapterPosition
                print("click long")
                if (position != RecyclerView.NO_POSITION) {
                    v?.setBackgroundColor(Color.parseColor("#545454"))
                    listener2.OnLongClickListener(position)
                }
                return false
            }
        }

        interface OnItemClickListener {
            fun onItemClick(position: Int)
        }
        interface OnLongClickListener {
            fun onItemLongClick(position: Int)
            abstract fun OnLongClickListener(position: Int)
        }
    }


}