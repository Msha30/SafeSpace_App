package com.example.safespace_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.safespace_app.login.Login
import com.example.safespace_app.signup.Signup

class Start : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnLogin = findViewById<Button>(R.id.btnlogin)
        val btnSignup = findViewById<Button>(R.id.btnsignup)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }

        btnSignup.setOnClickListener {
            startActivity(Intent(this, Signup::class.java))
        }
    }
}
