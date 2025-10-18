package com.cicero.ciceroai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.TextViewCompat
import com.cicero.ciceroai.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cicero.ciceroai.databinding.ActivityMainBinding
import com.google.android.material.chip.Chip
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var modelSpinnerAdapter: ArrayAdapter<String>
    private lateinit var standardModelSpinnerAdapter: ArrayAdapter<String>
    private var suppressModelSelection = false
    private var suppressStandardModelSelection = false

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

        standardModelSpinnerAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf()
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.standardModelSpinner.adapter = adapter
        }

        binding.standardModelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (suppressStandardModelSelection) {
                    return
                }
                viewModel.onStandardModelSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No-op
            }
        }

        binding.standardModelDownloadButton.setOnClickListener {
            viewModel.onStandardModelDownloadRequested()
        }

        binding.manualDownloadInputLayout.setEndIconOnClickListener {
            val url = binding.manualDownloadInput.text?.toString().orEmpty()
            viewModel.onManualDownloadRequested(url)
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
        binding.runButton.isEnabled = state.isRunButtonEnabled

        binding.downloadProgressIndicator.isVisible = state.downloadProgressVisible
        binding.downloadProgressIndicator.isIndeterminate = state.downloadProgressIndeterminate
        if (!state.downloadProgressIndeterminate) {
            binding.downloadProgressIndicator.setProgressCompat(state.downloadProgressValue, true)
        }
        binding.downloadProgressInfo.isVisible = state.downloadProgressVisible
        binding.downloadProgressPercent.text = state.downloadProgressPercentText
        binding.downloadProgressData.text = state.downloadProgressDataText

        binding.standardModelDownloadButton.isEnabled = state.isDownloadButtonEnabled
        binding.manualDownloadInputLayout.setEndIconVisible(state.isDownloadButtonEnabled)

        updateStandardModelSpinner(state.standardModels, state.selectedStandardModelIndex, state.isDownloadButtonEnabled)
        renderSelectedStandardModel(state)

        binding.promptInputLayout.error = state.promptError
        binding.outputView.text = state.outputText
        binding.logTicker.text = state.logMessages.lastOrNull() ?: getString(R.string.log_placeholder)

        updateModelSpinner(state.downloadedModels, state.selectedModelName)
        updateDownloadedModels(state.downloadedModels, state.selectedModelName)

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

    private fun updateDownloadedModels(models: List<String>, selectedModel: String?) {
        binding.downloadedModelsList.removeAllViews()
        if (models.isEmpty()) {
            binding.downloadedModelsList.isVisible = false
            binding.downloadedModelsEmpty.isVisible = true
            return
        }

        binding.downloadedModelsList.isVisible = true
        binding.downloadedModelsEmpty.isVisible = false
        models.forEachIndexed { index, modelName ->
            val card = createDownloadedModelCard(modelName, modelName == selectedModel)
            if (index == models.lastIndex) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = 0
            }
            binding.downloadedModelsList.addView(card)
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

    private fun updateStandardModelSpinner(
        models: List<StandardModelInfo>,
        selectedIndex: Int,
        isDownloadEnabled: Boolean
    ) {
        suppressStandardModelSelection = true
        standardModelSpinnerAdapter.clear()
        standardModelSpinnerAdapter.addAll(models.map { it.name })
        standardModelSpinnerAdapter.notifyDataSetChanged()

        val hasModels = models.isNotEmpty()
        binding.standardModelSpinner.isEnabled = hasModels && isDownloadEnabled
        binding.standardModelSpinner.isVisible = hasModels

        if (hasModels) {
            val index = selectedIndex.coerceIn(0, models.lastIndex)
            binding.standardModelSpinner.setSelection(index, false)
        } else {
            binding.standardModelName.text = getString(R.string.standard_models_empty)
            binding.standardModelSize.text = ""
            binding.standardModelDescription.text = ""
            binding.standardModelLinks.removeAllViews()
        }

        suppressStandardModelSelection = false
    }

    private fun renderSelectedStandardModel(state: MainUiState) {
        val model = state.standardModels.getOrNull(state.selectedStandardModelIndex)
        if (model == null) {
            binding.standardModelName.text = getString(R.string.standard_models_empty)
            binding.standardModelSize.text = ""
            binding.standardModelDescription.text = ""
            binding.standardModelLinks.removeAllViews()
            binding.standardModelDownloadButton.isEnabled = false
            return
        }

        binding.standardModelName.text = model.name
        binding.standardModelSize.text = getString(R.string.standard_model_size, model.sizeLabel)
        binding.standardModelDescription.text = model.description
        binding.standardModelDownloadButton.isEnabled = state.isDownloadButtonEnabled
        renderRepositoryLinks(model)
    }

    private fun renderRepositoryLinks(model: StandardModelInfo) {
        binding.standardModelLinks.removeAllViews()
        if (model.repositoryLinks.isEmpty()) {
            return
        }

        model.repositoryLinks.forEach { link ->
            val chip = Chip(this).apply {
                text = link.label
                isCheckable = false
                isClickable = true
                isFocusable = true
                setOnClickListener { openLink(link.url) }
            }
            binding.standardModelLinks.addView(chip)
        }
    }

    private fun createDownloadedModelCard(modelName: String, isSelected: Boolean): MaterialCardView {
        val context = this
        val card = MaterialCardView(context).apply {
            val padding = resources.getDimensionPixelSize(R.dimen.spacing_medium)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            this.layoutParams = layoutParams
            radius = resources.getDimension(R.dimen.downloaded_model_card_radius)
            strokeWidth = if (isSelected) resources.getDimensionPixelSize(R.dimen.downloaded_model_card_stroke) else 0
            strokeColor = MaterialColors.getColor(binding.root, AppCompatR.attr.colorPrimary)
            setContentPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            setOnClickListener { viewModel.onModelSelected(modelName) }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(context).apply {
            text = modelName
            TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_MaterialComponents_Subtitle1)
        }

        val subtitle = TextView(context).apply {
            text = if (isSelected) getString(R.string.downloaded_model_active) else getString(R.string.downloaded_model_select)
            TextViewCompat.setTextAppearance(this, MaterialR.style.TextAppearance_MaterialComponents_Body2)
        }

        container.addView(title)
        container.addView(subtitle)
        card.addView(container)
        return card
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
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
