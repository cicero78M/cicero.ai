package com.cicero.ciceroai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.cicero.ciceroai.llama.LlamaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: LlamaController
    private var prepareJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = LlamaController(this)
        binding.runButton.isEnabled = false
        binding.modelStatus.text = getString(R.string.model_status_loading)

        prepareJob = lifecycleScope.launch {
            try {
                val assetName = getString(R.string.asset_model_name)
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                controller.prepareSession(
                    assetName = assetName,
                    threadCount = threads,
                    contextSize = 2048
                )
                binding.modelStatus.text = getString(R.string.model_status_ready)
                binding.runButton.isEnabled = true
            } catch (error: Exception) {
                binding.modelStatus.text = error.localizedMessage ?: error.toString()
            }
        }

        binding.runButton.setOnClickListener {
            val prompt = binding.promptInput.text.toString().trim()
            if (prompt.isEmpty()) {
                binding.promptInput.error = getString(R.string.prompt_hint)
                return@setOnClickListener
            }

            binding.promptInput.error = null

            lifecycleScope.launch {
                binding.runButton.isEnabled = false
                binding.outputView.text = getString(R.string.inference_placeholder)
                try {
                    val result = controller.runInference(prompt, maxTokens = 256)
                    binding.outputView.text = result
                } catch (error: Exception) {
                    binding.outputView.text = error.localizedMessage ?: error.toString()
                } finally {
                    binding.runButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        controller.release()
        super.onDestroy()
    }
}
