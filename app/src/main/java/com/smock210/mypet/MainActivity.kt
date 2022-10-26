package com.smock210.mypet

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.clasess.NewPetAdapt
import com.vk.api.sdk.VK
import com.vk.api.sdk.VKApiCallback

import com.smock210.mypet.clasess.VKUser
import com.smock210.mypet.requests.VKUsersCommand
import com.vk.api.sdk.auth.VKAccessToken
import com.vk.api.sdk.auth.VKAuthCallback
import com.vk.api.sdk.auth.VKScope
import com.vk.api.sdk.exceptions.VKAuthException
import com.squareup.picasso.Picasso



class MainActivity : AppCompatActivity(), NewPetAdapt.NewPetadapt.OnItemClickListener, NewPetAdapt.NewPetadapt.OnLongClickListener {
    private val sList = arrayOf("One12", "Two","Three1","Emely").toList()
    private val adapter = NewPetAdapt.NewPetadapt(sList, this, this);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!VK.isLoggedIn()){
            requestUsers()
        }
        setContentView(R.layout.activity_main_start)


        val recyclerView: RecyclerView = findViewById(R.id.select_pet_start)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (VK.isLoggedIn()) {
            menuInflater.inflate(R.menu.title_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId){
            R.id.menu_vhod -> {
                VK.login(this, arrayListOf(VKScope.WALL, VKScope.EMAIL, VKScope.PHONE))

            }
            R.id.menu_registr -> {
                val intent = Intent(this@MainActivity, RegistrActivity::class.java)
                startActivity(intent)

            }

        }
        return super.onOptionsItemSelected(item)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val callback = object : VKAuthCallback {
            override fun onLogin(token: VKAccessToken) {
                val at = token.accessToken
                val uid = token.userId
                val emailUser = token.email
                val phoneUser = token.phone
                //Toast.makeText(applicationContext, at.toString(), Toast.LENGTH_SHORT).show()
// User passed authorization
                requestUsers()
                //menuInflater.inflate(R.menu.title_menu, menu)

            }

            override fun onLoginFailed(authException: VKAuthException) {
                Toast.makeText(this@MainActivity, "First", Toast.LENGTH_SHORT).show()
            }
        }
        if (data == null || !VK.onActivityResult(requestCode, resultCode, data, callback)) {
//super.onActivityResult(requestCode, resultCode, data)
            Toast.makeText(this@MainActivity, "First3", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemClick(position: Int) {

        var clickedItem = sList[position]
        Toast.makeText(this, "Item $position clicked $clickedItem", Toast.LENGTH_SHORT).show()
        clickedItem = "Clicked"
        adapter.notifyItemChanged(position)
    }

    override fun onItemLongClick(position: Int) {
        Toast.makeText(this, "Item $position long clicked ????", Toast.LENGTH_SHORT).show()
    }

    override fun OnLongClickListener(position: Int) {
        var clickedItem = sList[position]
        Toast.makeText(this, "Item $position long clicked $clickedItem", Toast.LENGTH_SHORT).show()
        clickedItem = "Clicked"
        adapter.notifyItemChanged(position)
    }

    private fun requestUsers() {
        VK.execute(VKUsersCommand(), object: VKApiCallback<List<VKUser>> {
            override fun success(result: List<VKUser>) {
                if (!isFinishing && !result.isEmpty()) {
                    val nameTV = findViewById<TextView>(R.id.nameTV)
                    val user = result[0]
                    nameTV.text = "${user.firstName} ${user.lastName}"
                    //nameTV.setOnClickListener(createOnClickListener(user.id))

                    val avatarIV = findViewById<ImageView>(R.id.avatarIV)
                    if (!TextUtils.isEmpty(user.photo)) {
                        Picasso.get()
                            .load(user.photo)
                            .error(R.drawable.user_placeholder)
                            .into(avatarIV)
                    } else {
                        avatarIV.setImageResource(R.drawable.user_placeholder)
                    }
                    //avatarIV.setOnClickListener(createOnClickListener(user.id))
                }
            }
            override fun fail(error: Exception) {
                Log.e(TAG, error.toString())
            }
        })
    }


}







