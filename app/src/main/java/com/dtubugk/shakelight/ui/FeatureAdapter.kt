package com.dtubugk.shakelight.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dtubugk.shakelight.databinding.ItemFeatureBinding

class FeatureAdapter(
    private val features: List<Feature>,
    private val onClick: (Feature) -> Unit,
) : RecyclerView.Adapter<FeatureAdapter.Holder>() {

    class Holder(val binding: ItemFeatureBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val feature = features[position]
        with(holder.binding) {
            icon.setImageResource(feature.icon)
            title.setText(feature.title)
            subtitle.setText(feature.subtitle)
            root.setOnClickListener { onClick(feature) }
        }
    }

    override fun getItemCount() = features.size
}
