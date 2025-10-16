package com.cicero.ciceroai

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cicero.ciceroai.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        binding.modelDownloadButton.setOnClickListener {
            viewModel.onDownloadButtonClicked()
        }

        binding.runButton.setOnClickListener {
            hideKeyboard()
            val prompt = binding.promptInput.text?.toString().orEmpty()
            viewModel.runInference(prompt)
        }

        binding.promptInput.doAfterTextChanged {
            viewModel.onPromptTextChanged()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        binding.modelStatus.text = state.modelStatus
        binding.modelDownloadButton.isEnabled = state.isDownloadButtonEnabled
        binding.runButton.isEnabled = state.isRunButtonEnabled

        binding.downloadProgressIndicator.isVisible = state.downloadProgressVisible
        binding.downloadProgressIndicator.isIndeterminate = state.downloadProgressIndeterminate
        if (!state.downloadProgressIndeterminate) {
            binding.downloadProgressIndicator.setProgressCompat(state.downloadProgressValue, true)
        }
        binding.downloadProgressLabel.isVisible = state.downloadProgressVisible
        binding.downloadProgressLabel.text = state.downloadProgressLabel

        binding.promptInputLayout.error = state.promptError
        binding.outputView.text = state.outputText

        val logText = if (state.logMessages.isEmpty()) {
            getString(R.string.log_placeholder)
        } else {
            state.logMessages.joinToString(separator = "\n") { message -> "• $message" }
        }
        binding.logView.text = logText
    }

    private fun hideKeyboard() {
        binding.promptInput.clearFocus()
        val view = binding.promptInput
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
    }
}
