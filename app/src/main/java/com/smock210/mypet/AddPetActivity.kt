package com.smock210.mypet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smock210.mypet.ui.AddPetFragment

class AddPetActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PET_ID = "pet_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_pet)

        if (savedInstanceState == null) {
            val petId = intent.getLongExtra(EXTRA_PET_ID, 0L)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddPetFragment.newInstance(petId))
                .commit()
        }
    }
}
