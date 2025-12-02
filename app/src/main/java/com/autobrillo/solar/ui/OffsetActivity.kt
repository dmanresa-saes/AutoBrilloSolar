package com.autobrillo.solar.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autobrillo.solar.ServiceLocator
import com.autobrillo.solar.databinding.ActivityOffsetBinding
import kotlinx.coroutines.launch

class OffsetActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOffsetBinding
    private val preferences by lazy { ServiceLocator.preferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOffsetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekOffset.max = 40

        lifecycleScope.launch {
            val current = preferences.currentPreferences().offsetPercent
            binding.seekOffset.progress = (current + 20).toInt()
            updateLabel(current)
        }

        binding.seekOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel((progress - 20).toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.buttonSave.setOnClickListener {
            val offset = binding.seekOffset.progress - 20
            lifecycleScope.launch {
                preferences.setOffset(offset.toFloat())
                finish()
            }
        }
    }

    private fun updateLabel(offset: Float) {
        binding.textOffsetValue.text = getString(com.autobrillo.solar.R.string.label_offset_value, offset)
    }

    companion object {
        fun launchIntent(context: Context): Intent = Intent(context, OffsetActivity::class.java)
    }
}
