package com.smock210.mypet.clasess

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.R

class ClassPetAdapter {
    class ListPetAdapter (context: Context): BaseAdapter() {
        var sList = arrayOf("One1", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen")
        private val mInflator: LayoutInflater = LayoutInflater.from(context)
        // Context context;

        override fun getCount(): Int {
            return sList.size
        }

        override fun getItem(position: Int): Any {
            return sList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            val view: View?
            val vh: ListRowHolder
            val vi: ListRowImage
            if (convertView == null) {
                view = this.mInflator.inflate(R.layout.list_row, parent, false)
                vh = ListRowHolder(view)
                vi = ListRowImage(view)

                if (view != null) {
                    view.tag = vh
                }
            } else {
                view = convertView
                vh = view.tag as ListRowHolder
                vi = view.background as ListRowImage
            }

            vh.label.text = sList[position]
            vi.image.setImageResource(R.drawable.ic_launcher_background)
            return view
        }
    }

        private fun init(function: () -> Unit) {

        }


        private class ListRowHolder(row: View?) {
            public val label: TextView = row?.findViewById(R.id.label) as TextView


        }
    private class ListRowImage(row: View) {
        public val image: ImageView = row.findViewById(R.id.imageViewPet) as ImageView


    }


}
