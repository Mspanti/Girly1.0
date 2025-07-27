package com.pant.girly

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AnonymousReportActivity : AppCompatActivity() {
    private lateinit var reportInput: EditText
    private lateinit var sendButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anonymous_report)

        reportInput = findViewById(R.id.reportInput)
        sendButton = findViewById(R.id.sendReportButton)

        sendButton.setOnClickListener {
            val report = reportInput.text.toString()
            if (report.isNotEmpty()) {
                Toast.makeText(this, "Report Submitted Anonymously!", Toast.LENGTH_SHORT).show()
                // TODO: Add Firebase anonymous report push here
                reportInput.text.clear()
            } else {
                Toast.makeText(this, "Please write something!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
