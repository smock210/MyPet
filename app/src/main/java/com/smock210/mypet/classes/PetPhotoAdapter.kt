package com.smock210.mypet.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.PetPhoto
import com.squareup.picasso.Picasso

class PetPhotoAdapter(
    private val onDelete: (PetPhoto) -> Unit,
    private val onSetPrimary: (PetPhoto) -> Unit
) : ListAdapter<PetPhoto, PetPhotoAdapter.Vh>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pet_photo, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = getItem(position)
        Picasso.get().load(item.photoUri).into(holder.image)
        holder.image.alpha = if (item.isPrimary) 1.0f else 0.6f
        holder.itemView.setOnClickListener { onSetPrimary(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.iv_photo)
        val delete: ImageButton = view.findViewById(R.id.btn_delete_photo)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PetPhoto>() {
            override fun areItemsTheSame(o: PetPhoto, n: PetPhoto) = o.id == n.id
            override fun areContentsTheSame(o: PetPhoto, n: PetPhoto) = o == n
        }
    }
}
