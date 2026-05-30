package com.yonsn76.kb

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class SongsAdapter(
    private val context: Context,
    private val songs: List<Song>,
    var currentPlayingIndex: Int = -1
) : BaseAdapter() {

    override fun getCount() = songs.size
    override fun getItem(position: Int) = songs[position]
    override fun getItemId(position: Int) = songs[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_song, parent, false)

        val song = songs[position]
        val isPlaying = position == currentPlayingIndex

        val tvNumber = view.findViewById<TextView>(R.id.tvNumber)
        val tvTitle = view.findViewById<TextView>(R.id.tvSongTitle)
        val tvArtist = view.findViewById<TextView>(R.id.tvSongArtist)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)

        tvNumber.text = "%02d".format(position + 1)
        tvTitle.text = song.title
        tvArtist.text = song.artist
        tvDuration.text = formatDuration(song.duration)
        view.setBackgroundResource(R.drawable.bg_glass_item)
        if (isPlaying) {
            tvNumber.text = "→"
            tvNumber.setTextColor(context.getColor(R.color.text_charcoal))
            tvTitle.setTextColor(context.getColor(R.color.text_charcoal))
            tvArtist.setTextColor(context.getColor(R.color.text_muted))
            tvDuration.setTextColor(context.getColor(R.color.text_charcoal))
            view.alpha = 1.0f
        } else {
            tvNumber.setTextColor(context.getColor(R.color.text_muted))
            tvTitle.setTextColor(context.getColor(R.color.text_charcoal))
            tvArtist.setTextColor(context.getColor(R.color.text_muted))
            tvDuration.setTextColor(context.getColor(R.color.text_muted))
            view.alpha = 0.7f
        }

        return view
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
