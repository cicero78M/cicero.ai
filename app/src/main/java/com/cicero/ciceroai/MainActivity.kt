package com.cicero.ciceroai

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modelSpinnerAdapter: ArrayAdapter<String>
    private var suppressModelSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        modelSpinnerAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.modelSpinner.adapter = adapter
        }

        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressModelSelection) {
                    return
                }
                val modelName = modelSpinnerAdapter.getItem(position) ?: return
                viewModel.onModelSelected(modelName)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op
            }
        }

        binding.topAppBar.setNavigationOnClickListener { showNavigationMenu() }

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

        binding.engineInput.doAfterTextChanged { text ->
            viewModel.onEngineSettingChanged(text?.toString().orEmpty())
        }

        binding.promptTemplateInput.doAfterTextChanged { text ->
            viewModel.onPromptTemplateChanged(text?.toString().orEmpty())
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
        binding.homeContainer.isVisible = state.currentPage == MainPage.HOME
        binding.downloadContainer.isVisible = state.currentPage == MainPage.DOWNLOAD
        binding.settingsContainer.isVisible = state.currentPage == MainPage.SETTINGS

        binding.topAppBar.title = when (state.currentPage) {
            MainPage.HOME -> getString(R.string.title_home)
            MainPage.DOWNLOAD -> getString(R.string.title_download)
            MainPage.SETTINGS -> getString(R.string.title_settings)
        }

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

        updateModelSpinner(state.downloadedModels, state.selectedModelName)
        updateDownloadedModels(state.downloadedModels)

        if (!binding.engineInput.isFocused) {
            val currentEngine = binding.engineInput.text?.toString().orEmpty()
            if (currentEngine != state.engineSetting) {
                binding.engineInput.setText(state.engineSetting)
                binding.engineInput.setSelection(binding.engineInput.text?.length ?: 0)
            }
        }

        if (!binding.promptTemplateInput.isFocused) {
            val currentTemplate = binding.promptTemplateInput.text?.toString().orEmpty()
            if (currentTemplate != state.promptTemplateSetting) {
                binding.promptTemplateInput.setText(state.promptTemplateSetting)
                binding.promptTemplateInput.setSelection(binding.promptTemplateInput.text?.length ?: 0)
            }
        }
    }

    private fun updateDownloadedModels(models: List<String>) {
        binding.downloadedModelsGroup.removeAllViews()
        if (models.isEmpty()) {
            binding.downloadedModelsGroup.isVisible = false
            binding.downloadedModelsEmpty.isVisible = true
            return
        }

        binding.downloadedModelsGroup.isVisible = true
        binding.downloadedModelsEmpty.isVisible = false
        models.forEach { modelName ->
            val chip = Chip(this).apply {
                text = modelName
                isCheckable = false
                isClickable = false
                isFocusable = false
            }
            binding.downloadedModelsGroup.addView(chip)
        }
    }

    private fun updateModelSpinner(models: List<String>, selectedModel: String?) {
        suppressModelSelection = true
        modelSpinnerAdapter.clear()
        modelSpinnerAdapter.addAll(models)
        modelSpinnerAdapter.notifyDataSetChanged()

        val hasModels = models.isNotEmpty()
        binding.modelSpinner.isEnabled = hasModels
        binding.modelSpinner.isVisible = hasModels
        binding.modelSelectorLabel.isVisible = hasModels

        if (hasModels) {
            val selectedIndex = selectedModel?.let { models.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            binding.modelSpinner.setSelection(selectedIndex, false)
        }

        suppressModelSelection = false
    }

    private fun showNavigationMenu() {
        PopupMenu(this, binding.topAppBar, Gravity.START).apply {
            menuInflater.inflate(R.menu.main_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_home -> {
                        viewModel.showHomePage()
                        true
                    }
                    R.id.action_download -> {
                        viewModel.showDownloadPage()
                        true
                    }
                    R.id.action_settings -> {
                        viewModel.showSettingsPage()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun hideKeyboard() {
        binding.promptInput.clearFocus()
        val view: View = binding.promptInput
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
    }
}
