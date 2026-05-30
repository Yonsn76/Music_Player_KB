package com.yonsn76.kb

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView

class FoldersAdapter(
    private val context: Context,
    private val folderUris: MutableList<Uri>,
    private val onRefresh: (Uri) -> Unit,
    private val onRemove: (Uri) -> Unit
) : BaseAdapter() {

    override fun getCount() = folderUris.size
    override fun getItem(position: Int) = folderUris[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_folder, parent, false)

        val uri = folderUris[position]
        val tvFolderName = view.findViewById<TextView>(R.id.tvFolderName)
        val tvFolderPath = view.findViewById<TextView>(R.id.tvFolderPath)
        val btnRefresh = view.findViewById<ImageButton>(R.id.btnRefreshFolder)
        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemoveFolder)

        // Extract a readable name from URI
        tvFolderName.text = try {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            treeId.split(":").lastOrNull() ?: treeId
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Carpeta"
        }

        tvFolderPath.text = uri.path ?: uri.toString()

        btnRefresh.setOnClickListener { onRefresh(uri) }
        btnRemove.setOnClickListener { onRemove(uri) }

        return view
    }
}