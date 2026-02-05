package com.matanh.transfer.ui.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.matanh.transfer.R
import com.matanh.transfer.databinding.FragmentFilesBinding
import com.matanh.transfer.ui.MainViewModel
import com.matanh.transfer.ui.activity.main.MainActivity
import com.matanh.transfer.util.FileAdapter
import com.matanh.transfer.util.FileItem

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter
    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            actionMode = mode
            mode?.menuInflater?.inflate(R.menu.contextual_action_menu, menu)
            (activity as? MainActivity)?.hideFAB()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.findItem(R.id.action_select_all)?.isVisible = adapter.itemCount > 0
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val selectedFiles = adapter.getSelectedFileItems()
            if (selectedFiles.isEmpty()) {
                showToast(R.string.no_files_selected)
                return false
            }

            return when (item?.itemId) {
                R.id.action_delete_contextual -> {
                    confirmDeleteMultipleFiles(selectedFiles)
                    true
                }

                R.id.action_share_contextual -> {
                    shareMultipleFiles(selectedFiles)
                    true
                }

                R.id.action_select_all -> {
                    adapter.selectAll()
                    updateActionModeTitle()
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            adapter.clearSelections()
            (activity as? MainActivity)?.showFAB()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentFilesBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        setupRecyclerView()
        setupSwipeRefresh()
        observeFiles()
    }

    private fun setupAdapter() {
        adapter = FileAdapter(files = emptyList(), onItemClick = { _, position ->
            if (actionMode != null) toggleSelection(position)
            else openFile(adapter.getFileItem(position))
        }, onItemLongClick = { _, position ->
            if (actionMode == null) {
                actionMode =
                    (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
            }
            toggleSelection(position)
            true
        })
    }

    private fun setupRecyclerView() {
        binding.projectList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FilesFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swiperefreshlayout.setOnRefreshListener {
            viewModel.refreshFiles()
        }

        // Observe refresh state
        viewModel.files.observe(viewLifecycleOwner) {
            binding.swiperefreshlayout.isRefreshing = false
        }
    }

    private fun observeFiles() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            adapter.updateFiles(files)
            binding.tvNoFilesMessage.isVisible = files.isEmpty()
            binding.swiperefreshlayout.isVisible = files.isNotEmpty()
        }
    }

    private fun toggleSelection(position: Int) {
        adapter.toggleSelection(position)
        updateActionModeTitle()
    }

    private fun updateActionModeTitle() {
        val count = adapter.getSelectedItemCount()
        if (count == 0) {
            actionMode?.finish()
        } else {
            actionMode?.apply {
                title = getString(R.string.selected_items_count, count)
                invalidate()
            }
        }
    }

    private fun openFile(fileItem: FileItem?) {
        val documentFile = fileItem?.let {
            DocumentFile.fromSingleUri(requireContext(), it.uri)
        } ?: run {
            showToast(R.string.file_not_found)
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentFile.uri, documentFile.type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with_title)))
        }.onFailure {
            showToast(R.string.no_app_to_open_file)
        }
    }

    private fun shareMultipleFiles(files: List<FileItem>) {
        val uris = files.mapNotNull { fileItem ->
            DocumentFile.fromSingleUri(requireContext(), fileItem.uri)?.takeIf { it.canRead() }?.uri
        }

        if (uris.isEmpty()) {
            showToast(R.string.no_readable_files_share)
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching {
            startActivity(
                Intent.createChooser(
                    shareIntent, getString(R.string.share_multiple_files_title, uris.size)
                )
            )
        }.onFailure {
            showToast(R.string.share_file_error, it.message)
        }

        actionMode?.finish()
    }

    private fun confirmDeleteMultipleFiles(files: List<FileItem>) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(
                getString(
                    R.string.confirm_delete_multiple_title,
                    files.size
                )
            ).setMessage(getString(R.string.confirm_delete_multiple_message, files.size))
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteFiles(files)
                actionMode?.finish()
            }.show()
    }

    private fun showToast(resId: Int, vararg args: Any?) {
        Toast.makeText(requireContext(), getString(resId, *args), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        _binding = null
    }
}