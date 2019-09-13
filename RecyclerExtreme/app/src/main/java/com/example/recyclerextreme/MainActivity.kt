package com.example.recyclerextreme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.view.View

const val CAMERA_INTENT = "com.example.recyclerextreme.CAMERA"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onCamera(view: View){
        val camIntent = Intent(this,CameraActivity::class.java)

        startActivity(camIntent)
    }
}
