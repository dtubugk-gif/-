package com.callsoundboard.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.callsoundboard.app.databinding.ItemSoundBinding
import com.callsoundboard.app.model.SoundClip

class SoundAdapter(
    private val onPlay: (SoundClip) -> Unit,
    private val onDelete: (SoundClip) -> Unit
) : RecyclerView.Adapter<SoundAdapter.VH>() {

    private val items = mutableListOf<SoundClip>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<SoundClip>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemSoundBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val clip = items[position]
        holder.binding.tvLabel.text = clip.label
        holder.binding.btnPlay.setOnClickListener { onPlay(clip) }
        holder.binding.btnDelete.setOnClickListener { onDelete(clip) }
    }

    override fun getItemCount(): Int = items.size
}
